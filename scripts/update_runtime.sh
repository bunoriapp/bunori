#!/usr/bin/env bash
set -e

# Repository containing the runtime releases
REPO="bunoriapp/bunori_lnreader_runtime"
TARGET_FILE="app/src/main/assets/ln_reader_runtime.js"
VERSION="${1:-latest}"

if [ "$VERSION" = "latest" ]; then
    URL="https://github.com/${REPO}/releases/latest/download/runtime.js"
    echo "Downloading latest runtime from ${URL}..."
else
    URL="https://github.com/${REPO}/releases/download/${VERSION}/runtime.js"
    echo "Downloading runtime ${VERSION} from ${URL}..."
fi

mkdir -p "$(dirname "$TARGET_FILE")"
curl -fSL "$URL" -o "$TARGET_FILE"

echo "Successfully updated ${TARGET_FILE} ($(wc -c < "$TARGET_FILE") bytes)!"
