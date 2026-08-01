#!/usr/bin/env bash
# Vendor darkrenaissance/darkfi at docs/upstream/darkfi-revision.txt into third_party/darkfi.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV_FILE="${ROOT}/docs/upstream/darkfi-revision.txt"
DEST="${ROOT}/third_party/darkfi"

first_line="$(sed -n '1p' "${REV_FILE}" | tr -d '[:space:]')"
if [[ ! "${first_line}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: invalid SHA on line 1 of ${REV_FILE}" >&2
  exit 1
fi

if [[ ! -d "${DEST}/.git" ]]; then
  git clone --filter=blob:none --depth 1 https://github.com/darkrenaissance/darkfi.git "${DEST}"
fi

(
  cd "${DEST}"
  git fetch --depth 1 origin "${first_line}"
  git checkout "${first_line}"
)

echo "Vendored darkfi @ ${first_line} → ${DEST}"
echo "Set DARKFI_SRC=${DEST} for scripts/build-darkirc-android.sh"
