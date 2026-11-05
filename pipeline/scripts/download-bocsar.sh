#!/usr/bin/env bash
# Downloads the BOCSAR recorded-crime-by-suburb zip (more than 10 MB) and unpacks it into data/raw/bocsar, then prints each file's name, size and
# first lines so the layout can be checked. Run by you. An existing download is kept; delete the folder to fetch it again.
# Usage: download-bocsar.sh [dest_dir]   (default: data/raw/bocsar)
# A download needs no database, so this does not source common.sh (and its pipeline/.env).
set -euo pipefail
PIPELINE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RAW_DIR="${RAW_DIR:-$PIPELINE_DIR/../data/raw}"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/bocsar.env"; set +a

DEST="${1:-$RAW_DIR/bocsar}"
ZIP="$DEST/bocsar_suburb_data.zip"
USER_AGENT="HomeFinder-personal-research/0.1"
mkdir -p "$DEST"

if [[ -f "$ZIP" ]]; then
  echo "bocsar: already downloaded ($ZIP)"
else
  curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$ZIP.part" "$BOCSAR_URL"
  # A zip starts with the bytes "PK"; an error page or a redirect to a web page does not.
  [[ "$(head -c 2 "$ZIP.part")" == "PK" ]] || { echo "Not a zip file; check BOCSAR_URL in config/bocsar.env (see the Data.NSW page)" >&2; rm -f "$ZIP.part"; exit 1; }
  unzip -tq "$ZIP.part" >/dev/null || { echo "The download is damaged (zip test failed); try again" >&2; rm -f "$ZIP.part"; exit 1; }
  mv "$ZIP.part" "$ZIP"
  sha256sum "$ZIP" | cut -d' ' -f1 > "$ZIP.sha256"
  echo "bocsar: downloaded $(du -h "$ZIP" | cut -f1)"
fi

if ! find "$DEST" -type f ! -name 'bocsar_suburb_data.zip*' | grep -q .; then
  unzip -q -o "$ZIP" -d "$DEST"
fi
echo "bocsar: files under $DEST:"
find "$DEST" -type f ! -name 'bocsar_suburb_data.zip*' | sort | while read -r f; do
  echo "  $f ($(du -h "$f" | cut -f1))"
done
echo "bocsar: first lines of each CSV (the layout):"
find "$DEST" -type f -iname '*.csv' | sort | while read -r f; do
  echo "--- $f"
  head -n 3 "$f" | cut -c1-400
  echo "    ($(wc -l < "$f") lines)"
done
