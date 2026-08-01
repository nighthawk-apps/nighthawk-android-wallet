package com.nighthawkapps.lib.android.ui.screen.send.model

/**
 * Multi-step Nighthawk send wizard.
 * Amount → Recipient → Memo → Review → Sending → Success | Failed
 *
 * OMR/OMD (PerfOMR) is always applied in Rust [build_transfer]/[broadcast_transfer]
 * (envelope + RegisterOmrClue); the UI does not offer a scheme toggle.
 */
enum class SendStage {
    Amount,
    Recipient,
    Memo,
    Review,
    Sending,
    Failed,
    Success,
}
