#!/usr/bin/env bash
# Workstation darkirc with clearnet P2P + adb reverse for emulator/device Chat testing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DARKIRC="${DARKIRC_BIN:-$ROOT/third_party/darkfi/target/release/darkirc}"
CFG="${DARKIRC_CONFIG:-/tmp/nighthawk-darkirc-host.toml}"

if [[ ! -x "$DARKIRC" ]]; then
  echo "Build host darkirc first: (cd $ROOT/third_party/darkfi/bin/darkirc && cargo build --release)"
  exit 1
fi

mkdir -p /tmp/nighthawk-darkirc/p2p /tmp/nighthawk-darkirc/darkirc_db

if [[ ! -f "$CFG" ]]; then
  cat > "$CFG" <<'EOF'
irc_listen = "tcp://127.0.0.1:6667"
dags_count = 8
datastore = "/tmp/nighthawk-darkirc/darkirc_db"
replay_datastore = "/tmp/nighthawk-darkirc/replayed_darkirc_db"
autojoin = ["#dev", "#media", "#hackers", "#memes", "#philosophy", "#markets", "#math", "#random", "#lunardao"]

[net]
magic_bytes = [251, 229, 199, 181]
p2p_datastore = "/tmp/nighthawk-darkirc/p2p"
hostlist = "/tmp/nighthawk-darkirc/p2p/p2p_hostlist.tsv"
active_profiles = ["tcp+tls"]

[net.profiles."tcp+tls"]
seeds = ["tcp+tls://lilith0.dark.fi:25551", "tcp+tls://lilith1.dark.fi:25551"]
EOF
fi

pkill -f 'target/release/darkirc.*nighthawk-darkirc-host' 2>/dev/null || true
nohup "$DARKIRC" --config "$CFG" > /tmp/nighthawk-darkirc-host.log 2>&1 &
echo "host darkirc pid=$! (log: /tmp/nighthawk-darkirc-host.log)"

if command -v adb >/dev/null; then
  adb reverse tcp:6667 tcp:6667
  echo "adb reverse tcp:6667 tcp:6667"
fi

echo "In the app: Chat → loopback 127.0.0.1:6667, embedded may auto-skip when port is in use."
echo "Wait for P2P on host (grep P2P / lilith in /tmp/nighthawk-darkirc-host.log), then open Chat."
