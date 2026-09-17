#!/usr/bin/env bash
# Attach locally built APK (and optional mapping.txt) to an existing GitHub Release.
# Usage:
#   Scripts/attach-release-asset.sh v0.4.1 ./app.apk
#   Scripts/attach-release-asset.sh v0.4.1 ./app.apk ./mapping.txt
set -euo pipefail

TAG="${1:-}"
shift || true
REPO="${REPO:-nodaysidle/nodaysidle-voice-anywhere-v2}"

if [[ -z "$TAG" || $# -lt 1 ]]; then
  echo "Usage: $0 <tag> <asset-path> [more-asset-paths...]" >&2
  exit 1
fi

for ASSET in "$@"; do
  if [[ ! -f "$ASSET" ]]; then
    echo "Asset not found: $ASSET" >&2
    exit 1
  fi
done

if ! gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "Release $TAG not found on $REPO. Push the tag first so CI can create the release, or:" >&2
  echo "  gh release create \"$TAG\" --repo \"$REPO\" --generate-notes --title \"Voice Anywhere $TAG\"" >&2
  exit 1
fi

gh release upload "$TAG" "$@" --repo "$REPO" --clobber
for ASSET in "$@"; do
  echo "Attached $(basename "$ASSET") → $TAG on $REPO"
done
