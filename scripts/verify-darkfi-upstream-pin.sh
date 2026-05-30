#!/usr/bin/env bash
# Fail if docs/upstream/darkfi-revision.txt SHA is malformed or GitHub raw blobs are missing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV_FILE="${ROOT}/docs/upstream/darkfi-revision.txt"

first_line="$(sed -n '1p' "${REV_FILE}" | tr -d '[:space:]')"
if [[ ! "${first_line}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: first line of ${REV_FILE} must be a full 40-char lowercase hex SHA; got '${first_line}'" >&2
  exit 1
fi

paths=(
  bin/drk/drk_config.toml
  bin/drk/src/lib.rs
  bin/darkirc/darkirc_config.toml
  bin/darkfid/src/rpc/mod.rs
  bin/darkfid/src/rpc/blockchain.rs
  bin/darkfid/src/rpc/tx.rs
  bin/darkfid/src/rpc/management.rs
  bin/darkfid/src/rpc/misc.rs
  bin/darkfid/src/rpc/stratum.rs
  bin/darkfid/src/rpc/xmr.rs
)

echo "Verifying darkrenaissance/darkfi@${first_line} …"
for rel in "${paths[@]}"; do
  url="https://raw.githubusercontent.com/darkrenaissance/darkfi/${first_line}/${rel}"
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${url}")"
  if [[ "${code}" != "200" ]]; then
    echo "error: HTTP ${code} for ${url}" >&2
    exit 1
  fi
done

echo "OK: all tracked paths reachable at pinned revision."
