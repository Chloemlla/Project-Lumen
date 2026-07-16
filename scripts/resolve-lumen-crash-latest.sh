#!/usr/bin/env bash
set -euo pipefail

# Resolve the latest Lumen Crash SDK main auto-release from GitHub Releases.
# Filters tags with prefix lumen-crash-v so non-SDK releases are ignored.

OWNER_REPO="${OWNER_REPO:-Chloemlla/Project-Lumen}"
API="https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100"
AUTH_HEADER=()
if [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${GH_TOKEN:-$GITHUB_TOKEN}")
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

VERSION="$(
  curl -fsSL "${AUTH_HEADER[@]}" \
    -H "Accept: application/vnd.github+json" \
    -H "User-Agent: lumen-crash-version-resolver" \
    "$API" \
  | jq -r '
      [.[]
        | select(.draft == false)
        | select(.tag_name | startswith("lumen-crash-v"))
      ]
      | sort_by(.published_at // .created_at)
      | reverse
      | .[0].tag_name
      | sub("^lumen-crash-v"; "")
    '
)"

if [ -z "$VERSION" ] || [ "$VERSION" = "null" ]; then
  echo "No lumen-crash release found" >&2
  exit 1
fi

echo "$VERSION"
