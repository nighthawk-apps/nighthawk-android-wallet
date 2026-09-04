#!/usr/bin/env bash
# Ensure third_party/darkfi matches docs/upstream/darkfi-revision.txt and compile
# event_graph *.zk.bin (required by the darkfi crate).
#
# Pin format: line 1 must start with a full 40-char lowercase hex SHA.
# Further tokens / later lines may be comments.
#
# DarkFi (and RandomX) are git submodules. F-Droid clones them with
# `submodules: true`. After a clone without --recurse-submodules, run this
# script (or `git submodule update --init`).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REV_FILE="${ROOT}/docs/upstream/darkfi-revision.txt"
DEST="${ROOT}/third_party/darkfi"

first_token="$(sed -n '1p' "${REV_FILE}" | awk '{print $1}')"
if [[ ! "${first_token}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: line 1 of ${REV_FILE} must start with a full 40-char lowercase hex SHA; got '${first_token}'" >&2
  exit 1
fi

gitmodules_path() {
  git -C "$ROOT" config --file "$ROOT/.gitmodules" --get "submodule.${1}.path" 2>/dev/null || true
}

if [[ -f "$ROOT/.gitmodules" && "$(gitmodules_path third_party/darkfi)" == "third_party/darkfi" ]]; then
  git -C "$ROOT" submodule update --init -- third_party/darkfi
  if [[ "$(gitmodules_path third_party/RandomX)" == "third_party/RandomX" ]]; then
    git -C "$ROOT" submodule update --init -- third_party/RandomX
  fi
elif [[ -d "${DEST}/bin/drk" && ! -e "${DEST}/.git" ]]; then
  # Bare tree (e.g. an unpacked tarball) — skip clone.
  echo "Using existing DarkFi tree at ${DEST} (no .git — skip clone)."
  DARKFI_SRC="$DEST" "$ROOT/scripts/compile-darkfi-zkas-proofs.sh"
  echo "Prepared darkfi @ ${first_token} → ${DEST}"
  exit 0
else
  if [[ ! -d "${DEST}/.git" ]]; then
    mkdir -p "$(dirname "${DEST}")"
    git clone --filter=blob:none https://github.com/nighthawk24/darkfi.git "${DEST}"
  fi
  (
    cd "${DEST}"
    git reset --hard HEAD >/dev/null
    git clean -fd >/dev/null
    git fetch --depth 1 origin "${first_token}"
    git checkout --detach "${first_token}"
  )
fi

if [[ -e "${DEST}/.git" ]]; then
  actual="$(git -C "${DEST}" rev-parse HEAD)"
  if [[ "${actual}" != "${first_token}" ]]; then
    echo "error: third_party/darkfi is ${actual}, expected ${first_token} (${REV_FILE})" >&2
    echo "Bump the submodule gitlink and the revision file together." >&2
    exit 1
  fi
fi

DARKFI_SRC="$DEST" "$ROOT/scripts/compile-darkfi-zkas-proofs.sh"

echo "Prepared darkfi @ ${first_token} → ${DEST}"
echo "Set DARKFI_SRC=${DEST} for scripts/build-darkirc-android.sh"
