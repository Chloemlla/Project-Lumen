#!/usr/bin/env bash
set -euo pipefail

# Download a Lumen Crash SDK GitHub Release into a local Maven-style repo tree.
# No GitHub Packages auth required; needs a public/readable release.
#
# Usage:
#   ./scripts/sync-lumen-crash-release-maven.sh [version] [output-dir]
# Example:
#   ./scripts/sync-lumen-crash-release-maven.sh 0.1.0-1a2b3c4d .m2-lumen-crash

OWNER_REPO="${OWNER_REPO:-Chloemlla/Project-Lumen}"
VERSION="${1:-}"
OUT_DIR="${2:-.m2-lumen-crash}"

if [ -z "$VERSION" ]; then
  if [ -x "./scripts/resolve-lumen-crash-latest.sh" ]; then
    VERSION="$(./scripts/resolve-lumen-crash-latest.sh)"
  else
    echo "Version argument required (or provide scripts/resolve-lumen-crash-latest.sh)." >&2
    exit 1
  fi
fi

TAG="lumen-crash-v${VERSION}"
API="https://api.github.com/repos/${OWNER_REPO}/releases/tags/${TAG}"
AUTH_HEADER=()
if [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${GH_TOKEN:-$GITHUB_TOKEN}")
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

curl -fsSL "${AUTH_HEADER[@]}" \
  -H "Accept: application/vnd.github+json" \
  -H "User-Agent: lumen-crash-release-sync" \
  "$API" > "$TMP_DIR/release.json"

mapfile -t ASSET_LINES < <(
  jq -r '.assets[] | select(.name|test("\\.(aar|pom|module|jar)$")) | "\(.name)\t\(.browser_download_url)"' \
    "$TMP_DIR/release.json"
)

if [ "${#ASSET_LINES[@]}" -eq 0 ]; then
  echo "No Maven assets found for tag ${TAG}" >&2
  exit 1
fi

BUNDLE_DIR="${OUT_DIR}/com/chloemlla/lumen/lumen-crash/${VERSION}"
CORE_DIR="${OUT_DIR}/com/chloemlla/lumen/lumen-crash-core/${VERSION}"
mkdir -p "$BUNDLE_DIR" "$CORE_DIR" "$TMP_DIR/assets"

for line in "${ASSET_LINES[@]}"; do
  name="${line%%$'\t'*}"
  url="${line#*$'\t'}"
  dest="$TMP_DIR/assets/$name"
  echo "Downloading $name"
  curl -fsSL "${AUTH_HEADER[@]}" -L "$url" -o "$dest"
  case "$name" in
    lumen-crash-core-*)
      cp "$dest" "$CORE_DIR/"
      ;;
    lumen-crash-*)
      cp "$dest" "$BUNDLE_DIR/"
      ;;
    *)
      cp "$dest" "$BUNDLE_DIR/"
      ;;
  esac
done

echo "Synced release ${VERSION} into ${OUT_DIR}"
echo "Gradle repo example:"
echo "  maven { url = uri(\"file://\$(pwd)/${OUT_DIR}\") }"
echo "  implementation(\"com.chloemlla.lumen:lumen-crash:${VERSION}\")"
