#!/usr/bin/env bash
# Vendor darkrenaissance/darkfi at docs/upstream/darkfi-revision.txt into third_party/darkfi.
#
# Pin format: line 1 must start with a full 40-char lowercase hex SHA.
# Further tokens / later lines may be comments.
#
# After checkout, compiles event_graph *.zk.bin (required by the darkfi crate).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV_FILE="${ROOT}/docs/upstream/darkfi-revision.txt"
DEST="${ROOT}/third_party/darkfi"

first_token="$(sed -n '1p' "${REV_FILE}" | awk '{print $1}')"
if [[ ! "${first_token}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: line 1 of ${REV_FILE} must start with a full 40-char lowercase hex SHA; got '${first_token}'" >&2
  exit 1
fi

if [[ ! -d "${DEST}/.git" ]]; then
  git clone --filter=blob:none https://github.com/darkrenaissance/darkfi.git "${DEST}"
fi

(
  cd "${DEST}"
  # Drop any local SQLCipher/drk overlays so the tree matches the pin exactly.
  # Avoid `git clean -x` so a pre-built target/ and zk.bin caches can be reused when present.
  git reset --hard HEAD >/dev/null
  git clean -fd >/dev/null
  git fetch --depth 1 origin "${first_token}"
  git checkout --detach "${first_token}"
)

DARKFI_SRC="$DEST" "$ROOT/scripts/compile-darkfi-zkas-proofs.sh"

echo "Vendored darkfi @ ${first_token} → ${DEST}"
echo "Set DARKFI_SRC=${DEST} for scripts/build-darkirc-android.sh"
