#!/usr/bin/env bash
# Compile event_graph *.zk → *.zk.bin required by the darkfi crate (include_bytes!).
# Safe to re-run; skips up-to-date outputs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${DARKFI_SRC:=$ROOT/third_party/darkfi}"
PROOF_DIR="$DARKFI_SRC/src/event_graph/proof"

if [[ ! -d "$PROOF_DIR" ]]; then
  echo "error: missing $PROOF_DIR — run scripts/vendor-darkfi.sh first" >&2
  exit 1
fi

resolve_zkas() {
  local candidate
  for candidate in \
    "${ZKAS_BIN:-}" \
    "$DARKFI_SRC/target/release/zkas" \
    "$ROOT/../darkfi/target/release/zkas"
  do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  echo "Building host zkas in $DARKFI_SRC ..." >&2
  (cd "$DARKFI_SRC" && cargo build --release -p zkas)
  printf '%s' "$DARKFI_SRC/target/release/zkas"
}

ZKAS="$(resolve_zkas)"
echo "Using zkas: $ZKAS"

shopt -s nullglob
local_missing=0
for zk in "$PROOF_DIR"/*.zk; do
  out="${zk}.bin"
  if [[ ! -f "$out" ]] || [[ "$zk" -nt "$out" ]]; then
    echo "zkas: $(basename "$zk")"
    "$ZKAS" "$zk" -o "$out"
    local_missing=1
  fi
done

if [[ "$local_missing" -eq 0 ]]; then
  echo "All event_graph zk.bin proofs up to date under $PROOF_DIR"
else
  echo "Wrote event_graph zk.bin proofs under $PROOF_DIR"
fi
