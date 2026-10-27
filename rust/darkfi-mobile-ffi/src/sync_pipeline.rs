//! OMR sync pipeline: prefetch window N+1 while applying window N.
//!
//! The pipeline overlaps network I/O (note commitments, nullifiers, sparse
//! block fetch) with CPU+DB work (Merkle tree append, coin insert, nullifier
//! mark, scan cursor persist). UnifOMR digest fetch stays in `try_omr_sync`.

use std::collections::BTreeMap;

/// Holds all fetched data for one OMR sync window, ready to be applied.
#[derive(Debug)]
pub struct OmrWindowResult {
    /// Start height of this window (inclusive).
    pub scan_start: u32,
    /// End height of this window (inclusive).
    pub scan_end: u32,
    /// Heights that the OMR digest says contain matching notes.
    pub matching_heights: Vec<u32>,
    /// Note commitment updates: `(height, Vec<coin_bytes>)`.
    pub commitment_updates: Vec<(u32, Vec<Vec<u8>>)>,
    /// Nullifier updates: `(height, Vec<nullifier_bytes>)`.
    pub nullifier_updates: Vec<(u32, Vec<Vec<u8>>)>,
    /// Sparse compact blocks fetched for matching heights.
    pub sparse_blocks: Vec<crate::lightwallet_client::LightCompactBlock>,
    /// Whether OMR was used (vs fallback trial decrypt).
    pub omr_used: bool,
}

/// Reorder buffer for in-order pipeline application.
///
/// Prefetch tasks may complete out of order (e.g., window 3 finishes before
/// window 2). The reorder buffer ensures `apply_omr_window` is called in
/// strict ascending order.
pub struct PipelineReorderBuffer {
    /// Buffered windows keyed by `scan_start`.
    buffer: BTreeMap<u32, OmrWindowResult>,
    /// The next `scan_start` we expect to apply.
    next_expected: u32,
}

impl PipelineReorderBuffer {
    /// Create a new reorder buffer expecting windows starting at `first_start`.
    pub fn new(first_start: u32) -> Self {
        Self {
            buffer: BTreeMap::new(),
            next_expected: first_start,
        }
    }

    /// Insert a completed window result. Returns any windows that are now
    /// ready to be applied in order.
    pub fn insert(&mut self, result: OmrWindowResult) -> Vec<OmrWindowResult> {
        self.buffer.insert(result.scan_start, result);
        let mut ready = Vec::new();
        while let Some(entry) = self.buffer.remove(&self.next_expected) {
            let next = entry.scan_end.saturating_add(1);
            ready.push(entry);
            self.next_expected = next;
        }
        ready
    }

    /// Number of buffered (out-of-order) windows waiting.
    pub fn buffered_count(&self) -> usize {
        self.buffer.len()
    }

    /// The next scan_start we're waiting for.
    pub fn next_expected(&self) -> u32 {
        self.next_expected
    }
}

/// Clamp `[scan_start, scan_end]` so it never starts below `birthday_height`.
/// Returns `None` when the whole window is pre-birthday.
pub fn clamp_window(scan_start: u32, scan_end: u32, birthday_height: u32) -> Option<(u32, u32)> {
    let clamped_start = scan_start.max(birthday_height);
    if clamped_start > scan_end {
        None
    } else {
        Some((clamped_start, scan_end))
    }
}

/// True when `height` is inside the inclusive scan window.
///
/// Padded `GetNoteCommitments` / `GetNullifiers` responses include a prefix
/// and suffix outside this window. Those heights must be dropped before
/// `tree.append` or a later cycle double-appends them.
pub fn in_scan_window(height: u32, scan_start: u32, scan_end: u32) -> bool {
    height >= scan_start && height <= scan_end
}

/// Drop commitment or nullifier rows whose height is outside
/// `[scan_start, scan_end]`.
pub fn filter_height_updates(
    updates: Vec<(u32, Vec<Vec<u8>>)>,
    scan_start: u32,
    scan_end: u32,
) -> Vec<(u32, Vec<Vec<u8>>)> {
    updates
        .into_iter()
        .filter(|(height, _)| in_scan_window(*height, scan_start, scan_end))
        .collect()
}

/// Padded note-commitment download. Heights outside `[start, end]` are dropped.
pub async fn fetch_padded_note_commitments(
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
    tip: u32,
) -> Result<Vec<(u32, Vec<Vec<u8>>)>, String> {
    if start > end {
        return Ok(Vec::new());
    }
    let (padded_start, padded_end) =
        crate::lightwallet_client::pad_block_range(start, end, tip.max(end));
    if padded_start > padded_end {
        return Err(format!(
            "padded note-commitment range {padded_start}..={padded_end} is empty"
        ));
    }
    let updates = client
        .get_note_commitments(padded_start, padded_end)
        .await?;
    Ok(filter_height_updates(updates, start, end))
}

/// Padded nullifier download. Heights outside `[start, end]` are dropped.
pub async fn fetch_padded_nullifiers(
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
    tip: u32,
) -> Result<Vec<(u32, Vec<Vec<u8>>)>, String> {
    if start > end {
        return Ok(Vec::new());
    }
    let (padded_start, padded_end) =
        crate::lightwallet_client::pad_block_range(start, end, tip.max(end));
    if padded_start > padded_end {
        return Err(format!(
            "padded nullifier range {padded_start}..={padded_end} is empty"
        ));
    }
    let updates = client.get_nullifiers(padded_start, padded_end).await?;
    Ok(filter_height_updates(updates, start, end))
}

/// Concurrent gRPC fetch of note commitments and nullifiers for one window.
///
/// The wire range is `pad_block_range`. Rows outside `[start, end]` are
/// removed before the caller appends them.
pub async fn fetch_commitments_and_nullifiers(
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
    tip: u32,
) -> Result<(Vec<(u32, Vec<Vec<u8>>)>, Vec<(u32, Vec<Vec<u8>>)>), String> {
    let (commitments_result, nullifiers_result) = tokio::join!(
        fetch_padded_note_commitments(client, start, end, tip),
        fetch_padded_nullifiers(client, start, end, tip),
    );
    Ok((commitments_result?, nullifiers_result?))
}

/// Prefetch all network data for one OMR sync window.
///
/// This function performs only reads (gRPC calls) — no DB mutations.
/// It is safe to call concurrently with `apply_omr_window` on a different window.
/// UnifOMR digest matching stays in `try_omr_sync`; this helper fetches the
/// commitments / nullifiers / sparse blocks used while applying a window.
pub async fn prefetch_omr_window(
    client: &crate::lightwallet_client::LightwalletClient,
    scan_start: u32,
    scan_end: u32,
    matching_heights: &[u32],
    birthday_height: u32,
    tip: u32,
) -> Result<OmrWindowResult, String> {
    let Some((clamped_start, clamped_end)) = clamp_window(scan_start, scan_end, birthday_height)
    else {
        return Ok(OmrWindowResult {
            scan_start,
            scan_end,
            matching_heights: vec![],
            commitment_updates: vec![],
            nullifier_updates: vec![],
            sparse_blocks: vec![],
            omr_used: false,
        });
    };

    let (commitment_updates, nullifier_updates) =
        fetch_commitments_and_nullifiers(client, clamped_start, clamped_end, tip).await?;

    let heights: Vec<u32> = matching_heights
        .iter()
        .copied()
        .filter(|h| in_scan_window(*h, clamped_start, clamped_end))
        .collect();

    // Block payloads are not fetched by exact match height. That request
    // would publish the UnifOMR result. The caller fills `sparse_blocks`
    // from batch PIR or `GetCompactOutputBodies` on the padded window.
    Ok(OmrWindowResult {
        scan_start,
        scan_end,
        matching_heights: heights,
        commitment_updates,
        nullifier_updates,
        sparse_blocks: Vec::new(),
        omr_used: !matching_heights.is_empty(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn empty_window(start: u32, end: u32) -> OmrWindowResult {
        OmrWindowResult {
            scan_start: start,
            scan_end: end,
            matching_heights: vec![],
            commitment_updates: vec![],
            nullifier_updates: vec![],
            sparse_blocks: vec![],
            omr_used: false,
        }
    }

    #[test]
    fn reorder_buffer_in_order() {
        let mut buf = PipelineReorderBuffer::new(100);
        let ready = buf.insert(empty_window(100, 199));
        assert_eq!(ready.len(), 1);
        assert_eq!(ready[0].scan_start, 100);
        assert_eq!(buf.next_expected(), 200);
    }

    #[test]
    fn reorder_buffer_out_of_order() {
        let mut buf = PipelineReorderBuffer::new(100);

        let ready = buf.insert(empty_window(200, 299));
        assert_eq!(ready.len(), 0);
        assert_eq!(buf.buffered_count(), 1);

        let ready = buf.insert(empty_window(100, 199));
        assert_eq!(ready.len(), 2);
        assert_eq!(ready[0].scan_start, 100);
        assert_eq!(ready[1].scan_start, 200);
        assert_eq!(buf.next_expected(), 300);
    }

    #[test]
    fn clamp_window_skips_pre_birthday() {
        assert_eq!(clamp_window(0, 999, 1000), None);
        assert_eq!(clamp_window(500, 1500, 1000), Some((1000, 1500)));
        assert_eq!(clamp_window(1000, 1500, 1000), Some((1000, 1500)));
        assert_eq!(clamp_window(0, 100, 0), Some((0, 100)));
    }

    #[test]
    fn padded_heights_outside_scan_window_are_dropped() {
        let (padded_start, padded_end) =
            crate::lightwallet_client::pad_block_range(42000, 42500, u32::MAX);
        assert!(padded_start < 42000);
        assert!(padded_end > 42500);
        let updates = vec![
            (padded_start, vec![vec![1]]),
            (42000, vec![vec![2]]),
            (42500, vec![vec![3]]),
            (padded_end, vec![vec![4]]),
        ];
        let kept = filter_height_updates(updates, 42000, 42500);
        let heights: Vec<u32> = kept.iter().map(|(h, _)| *h).collect();
        assert_eq!(heights, vec![42000, 42500]);
        assert!(kept.iter().all(|(h, _)| in_scan_window(*h, 42000, 42500)));
        assert!(!in_scan_window(padded_start, 42000, 42500));
    }

    #[test]
    fn reorder_buffer_follows_shrunk_window_end() {
        let mut buf = PipelineReorderBuffer::new(100);
        // complete=false shrank the window from 299 to 150.
        let ready = buf.insert(empty_window(100, 150));
        assert_eq!(ready.len(), 1);
        assert_eq!(buf.next_expected(), 151);
        let ready = buf.insert(empty_window(200, 299));
        assert!(ready.is_empty());
        assert_eq!(buf.buffered_count(), 1);
        let ready = buf.insert(empty_window(151, 199));
        assert_eq!(ready.len(), 2);
        assert_eq!(ready[0].scan_start, 151);
        assert_eq!(ready[1].scan_start, 200);
        assert_eq!(buf.next_expected(), 300);
    }
}
