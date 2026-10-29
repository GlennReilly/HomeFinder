#!/usr/bin/env bash
# Downloads the pinned G-NAF release into data/raw/gnaf, records its SHA-256 and unzips it.
# No checksum is published, so the first download records one (trust on first use) and later runs must match it.
source "$(dirname "$0")/common.sh"

DEST="$RAW_DIR/gnaf/$GNAF_RELEASE"
ZIP="$DEST/$GNAF_ZIP_NAME"
mkdir -p "$DEST"

if [[ ! -f "$ZIP" ]]; then
  curl --fail --location --continue-at - --output "$ZIP" "$GNAF_URL"
fi

ACTUAL="$(sha256sum "$ZIP" | cut -d' ' -f1)"
if [[ -f "$ZIP.sha256" ]]; then
  [[ "$ACTUAL" == "$(cat "$ZIP.sha256")" ]] || { echo "Checksum mismatch for $ZIP" >&2; exit 1; }
else
  echo "$ACTUAL" > "$ZIP.sha256"
fi

[[ -d "$DEST/unzipped" ]] || unzip -q "$ZIP" -d "$DEST/unzipped"
echo "G-NAF $GNAF_RELEASE ready in $DEST (sha256 $ACTUAL)"
