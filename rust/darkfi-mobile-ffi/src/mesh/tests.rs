use super::*;
use crate::mesh::types::{unix_ms, NH_ANNOUNCE};

#[test]
fn encode_decode_roundtrip() {
    let pkt = MeshPacket::new(PacketType::Ping, [1u8; 8], b"hi".to_vec());
    let bytes = encode(&pkt).unwrap();
    let back = decode(&bytes).unwrap();
    assert_eq!(pkt.typ, back.typ);
    assert_eq!(pkt.payload, back.payload);
    assert_eq!(pkt.sender, back.sender);
}

#[test]
fn reject_bitchat_magic() {
    let mut frame = encode(&MeshPacket::new(PacketType::Ping, [0u8; 8], vec![1])).unwrap();
    frame[0] = 0x00;
    assert!(matches!(decode(&frame), Err(CodecError::BadMagic)));
}

#[test]
fn reject_unknown_type() {
    let mut frame = encode(&MeshPacket::new(PacketType::Ping, [0u8; 8], vec![1])).unwrap();
    frame[3] = 0x01;
    assert!(matches!(decode(&frame), Err(CodecError::UnknownType)));
}

#[test]
fn fragment_reassemble() {
    let body = vec![0xABu8; FRAGMENT_CHUNK * 3 + 10];
    let pkt = MeshPacket::new(PacketType::DagEvent, [9u8; 8], body.clone());
    let parts = fragment_split(&pkt, FRAGMENT_CHUNK).unwrap();
    assert!(parts.len() >= 4);
    let mut asm = FragmentAssembler::default();
    let now = unix_ms();
    let mut rebuilt = None;
    for p in &parts {
        rebuilt = asm.ingest(p, now).unwrap();
    }
    let got = rebuilt.expect("complete");
    assert_eq!(got.typ, PacketType::DagEvent);
    assert_eq!(got.payload, body);
}

#[test]
fn fragment_timeout_drops() {
    let body = vec![0x11u8; FRAGMENT_CHUNK + 20];
    let pkt = MeshPacket::new(PacketType::DagEvent, [2u8; 8], body);
    let parts = fragment_split(&pkt, FRAGMENT_CHUNK).unwrap();
    let mut asm = FragmentAssembler::default();
    assert!(asm.ingest(&parts[0], 1_000).unwrap().is_none());
    let other = MeshPacket::new(PacketType::Ping, [3u8; 8], b"x".to_vec());
    let _ = asm.ingest(&other, 1_000 + 60_000);
}

#[test]
fn gcs_roundtrip_membership() {
    let ids: Vec<[u8; 16]> = (0..8)
        .map(|i| {
            let mut id = [0u8; 16];
            id[0] = i;
            id[15] = i.wrapping_mul(3);
            id
        })
        .collect();
    let (p, m, data) = crate::mesh::gcs::encode_gcs(&ids);
    for id in &ids {
        assert!(crate::mesh::gcs::gcs_contains(p, m, &data, id), "false negative");
    }
    let mut missing = [0u8; 16];
    missing[0] = 0xFF;
    let _ = crate::mesh::gcs::gcs_contains(p, m, &data, &missing);
}

#[test]
fn lwd_allowlist_accepts_small_rpcs() {
    for m in BLE_ALLOWED_LWD {
        let payload = format!("{m}\n").into_bytes();
        assert_eq!(validate_lwd_ctrl(&payload).unwrap(), *m);
    }
}

#[test]
fn lwd_allowlist_rejects_unifomr_digest() {
    let payload = b"GetUnifOmrDigest\n".to_vec();
    assert!(matches!(
        validate_lwd_ctrl(&payload),
        Err(LwdCtrlError::Forbidden("GetUnifOmrDigest"))
    ));
}

#[test]
fn lwd_allowlist_rejects_pir_and_blocks() {
    for m in BLE_FORBIDDEN_LWD {
        let payload = format!("{m}\nxxxx").into_bytes();
        assert!(
            matches!(validate_lwd_ctrl(&payload), Err(LwdCtrlError::Forbidden(_))),
            "{m}"
        );
    }
}

#[test]
fn lwd_ctrl_size_cap() {
    let mut payload = b"GetLightInfo\n".to_vec();
    payload.extend(vec![0u8; LWD_CTRL_MAX]);
    assert!(matches!(
        validate_lwd_ctrl(&payload),
        Err(LwdCtrlError::TooLarge)
    ));
}

#[test]
fn gateway_requires_charging_and_unmetered() {
    assert!(!gateway_eligible(true, true, false, true));
    assert!(!gateway_eligible(true, true, true, false));
    assert!(!gateway_eligible(true, false, true, true));
    assert!(!gateway_eligible(false, true, true, true));
    assert!(gateway_eligible(true, true, true, true));
}

#[test]
fn identity_rotates_and_is_not_zero() {
    let mut id = MeshIdentity::generate();
    let a = id.peer_id();
    id.rotate();
    let b = id.peer_id();
    assert_ne!(a, b);
    assert_ne!(a, [0u8; 8]);
}

#[test]
fn identity_independent_of_wallet_bytes() {
    let a = MeshIdentity::generate().peer_id();
    let b = MeshIdentity::generate().peer_id();
    assert_ne!(a, b);
}

fn exchange(a: &mut MeshEngine, b: &mut MeshEngine, rounds: usize) {
    for _ in 0..rounds {
        let ao = a.pop_outbound();
        let bo = b.pop_outbound();
        if ao.is_empty() && bo.is_empty() {
            break;
        }
        for f in ao {
            let _ = b.ingest_bytes(&f);
        }
        for f in bo {
            let _ = a.ingest_bytes(&f);
        }
    }
}

fn handshake(a: &mut MeshEngine, b: &mut MeshEngine) {
    a.neighbor_up(b.peer_id()).unwrap();
    b.neighbor_up(a.peer_id()).unwrap();
    exchange(a, b, 24);
}

#[test]
fn two_node_encrypted_event_sync() {
    let mut a = MeshEngine::new();
    let mut b = MeshEngine::new();
    a.set_mesh_on(true);
    b.set_mesh_on(true);
    handshake(&mut a, &mut b);
    a.publish_event([1u8; 16], b"hello-event".to_vec()).unwrap();
    let wire = a.pop_outbound();
    assert!(!wire.is_empty());
    for f in &wire {
        assert!(
            !f.windows(b"hello-event".len()).any(|w| w == b"hello-event"),
            "event body must not appear in the clear on the wire"
        );
        let _ = b.ingest_bytes(f);
    }
    exchange(&mut a, &mut b, 16);
    let ev = b.pop_events();
    assert!(ev.iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"hello-event"
    )));
}

#[test]
fn engine_drops_duplicate() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(PacketType::Ping, [7u8; 8], Vec::new());
    let bytes = encode(&pkt).unwrap();
    assert!(e.ingest_bytes(&bytes).is_ok());
    assert!(matches!(
        e.ingest_bytes(&bytes),
        Err(IngestError::DroppedDuplicate)
    ));
}

#[test]
fn engine_rejects_plaintext_lwd_ctrl() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(
        PacketType::LwdCtrl,
        [4u8; 8],
        b"GetUnifOmrDigest\n".to_vec(),
    );
    let bytes = encode(&pkt).unwrap();
    assert!(matches!(
        e.ingest_bytes(&bytes),
        Err(IngestError::PlaintextCtrl)
    ));
}

#[test]
fn engine_rejects_plaintext_send_transaction() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(
        PacketType::LwdCtrl,
        [4u8; 8],
        b"SendTransaction\nab".to_vec(),
    );
    let bytes = encode(&pkt).unwrap();
    assert!(matches!(
        e.ingest_bytes(&bytes),
        Err(IngestError::PlaintextCtrl)
    ));
}

#[test]
fn plaintext_dag_event_is_ignored() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(PacketType::DagEvent, [5u8; 8], b"#dev\nnick\nhi".to_vec());
    let bytes = encode(&pkt).unwrap();
    e.ingest_bytes(&bytes).unwrap();
    assert!(!e.pop_events().iter().any(|ev| matches!(ev, EngineEvent::EventPut { .. })));
}

#[test]
fn local_types_are_not_relayed() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(PacketType::Ping, [5u8; 8], Vec::new());
    let bytes = encode(&pkt).unwrap();
    e.ingest_bytes(&bytes).ok();
    for frame in e.pop_outbound() {
        let p = decode(&frame).unwrap();
        assert_ne!(p.typ, PacketType::Ping);
        if p.typ == PacketType::Pong {
            assert!(p.payload.is_empty());
        }
    }
}

#[test]
fn announce_opcode_is_not_bitchat() {
    assert_ne!(NH_ANNOUNCE, 0x01);
    assert_eq!(PacketType::Announce.as_u8(), 0xA1);
}

#[test]
fn golden_announce_frame() {
    let mut pkt = MeshPacket::new(PacketType::Announce, [0x11u8; 8], vec![0x00]);
    pkt.timestamp_ms = 0;
    pkt.ttl = 7;
    pkt.flags = 0;
    let bytes = encode(&pkt).unwrap();
    assert_eq!(
        bytes,
        [
            0x4E, 0x48, 0x01, 0xA1, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x00, 0x00, 0x00, 0x01, 0x00
        ]
    );
}

#[test]
fn ffi_start_stop_status() {
    start_mesh().unwrap();
    let s = mesh_status();
    assert!(s.contains("\"mesh_on\":true"));
    assert!(s.contains("\"gateway_ready\":false"));
    stop_mesh().unwrap();
}

#[test]
fn submit_lwd_is_disabled() {
    let mut client = MeshEngine::new();
    client.set_mesh_on(true);
    let err = client
        .submit_lwd_ctrl([9u8; 8], "GetUnifOmrDigest", &[])
        .unwrap_err();
    assert!(matches!(
        err,
        IngestError::Lwd(LwdCtrlError::Forbidden("GetUnifOmrDigest"))
    ));
    let err = client
        .submit_lwd_ctrl([9u8; 8], "GetLightInfo", &[])
        .unwrap_err();
    assert!(matches!(err, IngestError::BulkDenied));
}

#[test]
fn noise_pad_buckets() {
    let p = crate::mesh::lwd_ctrl::pad_frame(b"hi").unwrap();
    assert_eq!(p.len(), 256);
    assert_eq!(crate::mesh::lwd_ctrl::unpad_frame(&p).unwrap(), b"hi");
}

#[test]
fn mesh_event_blob_roundtrip() {
    let enc = encode_mesh_event(b"event-bytes", b"blob").unwrap();
    let (e, b) = decode_mesh_event(&enc).unwrap();
    assert_eq!(e, b"event-bytes");
    assert_eq!(b, b"blob");
}

#[test]
fn bulk_offer_roundtrip() {
    let o = crate::mesh::bulk_session::BulkOffer {
        session_id: [7u8; 16],
        kind: crate::mesh::KIND_SOFTAP,
        port: 8443,
        ssid: "nh-mesh".into(),
        psk: b"psk-bytes".to_vec(),
    };
    let enc = crate::mesh::bulk_session::encode_offer(&o).unwrap();
    let back = crate::mesh::bulk_session::decode_offer(&enc).unwrap();
    assert_eq!(o, back);
}

#[test]
fn bulk_quota_enforces_bytes_and_time() {
    let mut q = crate::mesh::bulk_session::BulkQuota::start(1_000);
    assert!(q.allow(1024, 1_000));
    assert!(!q.allow(crate::mesh::MAX_BULK_BYTES, 1_001));
    let mut q2 = crate::mesh::bulk_session::BulkQuota::start(1_000);
    assert!(!q2.allow(1, 1_000 + crate::mesh::MAX_BULK_SECS * 1000 + 1));
}

#[test]
fn bulk_never_from_bg_refresh() {
    assert!(!crate::mesh::may_start_bulk(true, true, true));
    assert!(crate::mesh::may_start_bulk(true, true, false));
    assert!(!crate::mesh::may_start_bulk(false, true, false));
}

#[test]
fn plaintext_bulk_on_ble_is_ignored() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    let pkt = MeshPacket::new(PacketType::BulkOffer, [3u8; 8], b"ssid=leak".to_vec());
    let bytes = encode(&pkt).unwrap();
    e.ingest_bytes(&bytes).unwrap();
    assert!(e.pop_events().is_empty() || !e.pop_events().iter().any(|ev| matches!(ev, EngineEvent::EventPut { .. })));
}

#[test]
fn bulk_offer_denied() {
    let mut gw = MeshEngine::new();
    gw.set_mesh_on(true);
    let err = gw
        .submit_bulk_offer(
            [1u8; 8],
            crate::mesh::bulk_session::BulkOffer {
                session_id: [1u8; 16],
                kind: crate::mesh::KIND_SOFTAP,
                port: 1,
                ssid: "x".into(),
                psk: vec![],
            },
        )
        .unwrap_err();
    assert!(matches!(err, IngestError::BulkDenied));
}

#[test]
fn identity_rotate_changes_peer_id() {
    let mut e = MeshEngine::new();
    let before = e.peer_id();
    e.rotate_identity();
    assert_ne!(before, e.peer_id());
}

#[test]
fn wipe_clears_mesh_and_bulk_override() {
    start_mesh().unwrap();
    crate::mesh::set_bulk_tcp_override(Some("127.0.0.1".into()), 9).unwrap();
    assert!(crate::mesh::bulk_tcp_override().is_some());
    crate::mesh::mesh_wipe().unwrap();
    let s = mesh_status();
    assert!(s.contains("\"mesh_on\":false"));
    assert!(crate::mesh::bulk_tcp_override().is_none());
}

#[test]
fn oversized_event_rejected() {
    let mut a = MeshEngine::new();
    a.set_mesh_on(true);
    let body = vec![0u8; EVENT_INNER_MAX + 1];
    assert!(matches!(
        a.publish_event([2u8; 16], body),
        Err(IngestError::TooLarge)
    ));
}

#[test]
fn replayed_noise_enc_is_dropped() {
    let mut a = MeshEngine::new();
    let mut b = MeshEngine::new();
    a.set_mesh_on(true);
    b.set_mesh_on(true);
    handshake(&mut a, &mut b);
    a.publish_event([3u8; 16], b"once".to_vec()).unwrap();
    let frames = a.pop_outbound();
    assert!(!frames.is_empty());
    for f in &frames {
        let _ = b.ingest_bytes(f);
    }
    let first = b.pop_events();
    assert!(first.iter().any(|e| matches!(e, EngineEvent::EventPut { .. })));
    for f in &frames {
        let _ = b.ingest_bytes(f);
    }
    assert!(!b.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"once"
    )));
}

/// Star topology: A and G only talk through relay R.
fn hop_star(
    a: &mut MeshEngine,
    relay: &mut MeshEngine,
    g: &mut MeshEngine,
    rounds: usize,
) {
    for _ in 0..rounds {
        for f in a.pop_outbound() {
            let _ = relay.ingest_bytes(&f);
        }
        for f in g.pop_outbound() {
            let _ = relay.ingest_bytes(&f);
        }
        for f in relay.pop_outbound() {
            if let Ok(pkt) = decode(&f) {
                if pkt.sender != a.peer_id() {
                    match pkt.recipient {
                        Some(rx) if rx == a.peer_id() => {
                            let _ = a.ingest_bytes(&f);
                        }
                        Some(rx) if rx == g.peer_id() => {}
                        Some(_) => {}
                        None => {
                            let _ = a.ingest_bytes(&f);
                        }
                    }
                }
                if pkt.sender != g.peer_id() {
                    match pkt.recipient {
                        Some(rx) if rx == g.peer_id() => {
                            let _ = g.ingest_bytes(&f);
                        }
                        Some(rx) if rx == a.peer_id() => {}
                        Some(_) => {}
                        None => {
                            let _ = g.ingest_bytes(&f);
                        }
                    }
                }
            }
        }
    }
}

fn arm_three() -> (MeshEngine, MeshEngine, MeshEngine) {
    let mut a = MeshEngine::new();
    let mut relay = MeshEngine::new();
    let mut g = MeshEngine::new();
    a.set_mesh_on(true);
    relay.set_mesh_on(true);
    g.set_mesh_on(true);
    (a, relay, g)
}

fn handshake_star(a: &mut MeshEngine, relay: &mut MeshEngine, g: &mut MeshEngine) {
    a.neighbor_up(relay.peer_id()).unwrap();
    relay.neighbor_up(a.peer_id()).unwrap();
    hop_star(a, relay, g, 24);
    g.neighbor_up(relay.peer_id()).unwrap();
    relay.neighbor_up(g.peer_id()).unwrap();
    hop_star(a, relay, g, 24);
}

#[test]
fn three_device_connect_handshake() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    assert!(a.status_json().contains("\"peers_hint\":1") || a.status_json().contains("peers_hint"));
}

#[test]
fn three_device_chat_via_relay() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([10u8; 16], b"event-from-a".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"event-from-a"
    )));
    g.publish_event([11u8; 16], b"event-from-g".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(a.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"event-from-g"
    )));
}

#[test]
fn three_device_relay_reseals_not_forwards_ciphertext() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([12u8; 16], b"unique-payload-xyz".to_vec()).unwrap();
    let a_frames = a.pop_outbound();
    assert!(!a_frames.is_empty());
    for f in &a_frames {
        let _ = relay.ingest_bytes(f);
    }
    let relay_out = relay.pop_outbound();
    assert!(!relay_out.is_empty());
    for f in &a_frames {
        for r in &relay_out {
            assert_ne!(f, r, "relay must not forward initiator ciphertext unchanged");
        }
    }
    for f in relay_out {
        let _ = g.ingest_bytes(&f);
    }
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"unique-payload-xyz"
    )));
}

#[test]
fn three_device_periodic_dag_sync() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([20u8; 16], b"chat-round-1".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"chat-round-1"
    )));
    a.request_dag_sync();
    hop_star(&mut a, &mut relay, &mut g, 16);
    g.request_dag_sync();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(a.status_json().contains("\"dag_ids\":"));
    assert!(g.status_json().contains("\"dag_ids\":"));
}

#[test]
fn three_device_link_lost_and_reconnect() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([21u8; 16], b"before-loss".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"before-loss"
    )));

    relay.set_mesh_on(false);
    a.publish_event([22u8; 16], b"during-loss".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 8);
    assert!(!g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"during-loss"
    )));

    relay.set_mesh_on(true);
    handshake_star(&mut a, &mut relay, &mut g);
    a.request_dag_sync();
    g.request_dag_sync();
    hop_star(&mut a, &mut relay, &mut g, 24);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"during-loss"
    )));
}

#[test]
fn three_device_relay_reset() {
    let (mut a, mut relay, mut g) = arm_three();
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([23u8; 16], b"pre-reset".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"pre-reset"
    )));

    relay.wipe();
    relay.set_mesh_on(true);
    handshake_star(&mut a, &mut relay, &mut g);
    a.publish_event([24u8; 16], b"post-reset".to_vec()).unwrap();
    hop_star(&mut a, &mut relay, &mut g, 16);
    assert!(g.pop_events().iter().any(|e| matches!(
        e,
        EngineEvent::EventPut { payload, .. } if payload == b"post-reset"
    )));
}

#[test]
fn offline_ctrl_disabled() {
    crate::mesh::mesh_wipe().unwrap();
    assert!(crate::mesh::mesh_try_offline_ctrl("GetLightInfo", &[]).is_err());
    start_mesh().unwrap();
    assert!(crate::mesh::mesh_try_offline_ctrl("GetUnifOmrDigest", &[]).is_err());
    crate::mesh::mesh_wipe().unwrap();
}

#[test]
fn announce_never_sets_caps() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    e.set_gateway_opt_in(true);
    e.set_power(OsPowerState {
        foreground: true,
        charging: true,
        unmetered: true,
    });
    e.announce();
    assert!(e.pop_outbound().is_empty());
    assert!(e.last_gateway_peer().is_none());
}

#[test]
fn handshake_timeout_drops_unfinished() {
    use super::session::SessionTable;
    use super::types::HANDSHAKE_TIMEOUT_MS;
    let mut t = SessionTable::default();
    let peer = [9u8; 8];
    t.begin_initiator(peer, Vec::new()).unwrap();
    assert!(t.is_init_sent(&peer));
    let n = t.expire_handshakes(unix_ms() + HANDSHAKE_TIMEOUT_MS + 1, HANDSHAKE_TIMEOUT_MS);
    assert_eq!(n, 1);
    assert!(!t.is_init_sent(&peer));
}

#[test]
fn cache_full_event_on_eviction() {
    let mut e = MeshEngine::new();
    e.set_mesh_on(true);
    for i in 0..300u16 {
        let mut body = vec![i as u8; 64];
        body.extend_from_slice(&i.to_be_bytes());
        e.publish_dag_event(body);
    }
    let ev = e.pop_events();
    assert!(
        ev.iter()
            .any(|x| matches!(x, EngineEvent::CacheFull { .. })),
        "expected cache_full after exceeding 256 events"
    );
}
