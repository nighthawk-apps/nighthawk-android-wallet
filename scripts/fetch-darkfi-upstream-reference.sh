#!/usr/bin/env bash
# Download pinned upstream paths into docs/upstream/_scratch/ for manual diff (directory is gitignored).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV_FILE="${ROOT}/docs/upstream/darkfi-revision.txt"
OUT="${ROOT}/docs/upstream/_scratch"

first_line="$(sed -n '1p' "${REV_FILE}" | tr -d '[:space:]')"
if [[ ! "${first_line}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: invalid SHA on line 1 of ${REV_FILE}" >&2
  exit 1
fi

paths=(
  bin/drk/drk_config.toml
  bin/drk/src/lib.rs
  bin/app/src/plugin/drk.rs
  bin/app/src/plugin/darkirc.rs
  bin/darkirc/darkirc_config.toml
  bin/darkirc/src/settings.rs
  bin/darkfid/src/rpc/mod.rs
  bin/darkfid/src/rpc/blockchain.rs
  bin/darkfid/src/rpc/tx.rs
  bin/darkfid/src/rpc/management.rs
  bin/darkfid/src/rpc/misc.rs
  bin/darkfid/src/rpc/stratum.rs
  bin/darkfid/src/rpc/xmr.rs
)

rm -rf "${OUT}"
mkdir -p "${OUT}"

echo "Fetching darkrenaissance/darkfi@${first_line} → ${OUT}"
for rel in "${paths[@]}"; do
  dest="${OUT}/${rel}"
  mkdir -p "$(dirname "${dest}")"
  curl -fsSL "https://raw.githubusercontent.com/darkrenaissance/darkfi/${first_line}/${rel}" -o "${dest}"
  echo "  wrote ${rel}"
done

echo "Done. Diff these trees against docs/code expectations (Kotlin RPC constants, endpoints, chat presets)."
