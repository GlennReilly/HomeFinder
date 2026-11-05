#!/usr/bin/env bash
# Downloads the ABS SEIFA 2021 workbook for Suburbs and Localities (about 7 MB) into data/raw/seifa. Run by you. An existing download is
# kept; delete the folder to fetch it again.
# Usage: download-seifa.sh [dest_dir]   (default: data/raw/seifa)
# A download needs no database, so this does not source common.sh (and its pipeline/.env).
set -euo pipefail
PIPELINE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RAW_DIR="${RAW_DIR:-$PIPELINE_DIR/../data/raw}"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/seifa.env"; set +a

DEST="${1:-$RAW_DIR/seifa}"
FILE="$DEST/seifa-2021-sal.xlsx"
USER_AGENT="HomeFinder-personal-research/0.1"
mkdir -p "$DEST"

if [[ -f "$FILE" ]]; then
  echo "seifa: already downloaded ($FILE)"
else
  curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$FILE.part" "$SEIFA_URL"
  # An xlsx is a zip, which starts with the bytes "PK"; an error page or a redirect to a web page does not.
  [[ "$(head -c 2 "$FILE.part")" == "PK" ]] || { echo "Not an Excel workbook; check SEIFA_URL in config/seifa.env (see the ABS SEIFA page)" >&2; rm -f "$FILE.part"; exit 1; }
  unzip -tq "$FILE.part" >/dev/null || { echo "The download is damaged (zip test failed); try again" >&2; rm -f "$FILE.part"; exit 1; }
  mv "$FILE.part" "$FILE"
  sha256sum "$FILE" | cut -d' ' -f1 > "$FILE.sha256"
  echo "seifa: downloaded $(du -h "$FILE" | cut -f1)"
fi
echo "seifa: $FILE"
