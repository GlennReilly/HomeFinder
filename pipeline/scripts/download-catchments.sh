#!/usr/bin/env bash
# Downloads the NSW Department of Education school catchment (intake zone) boundaries, a ZIP, into data/raw/catchments, unpacks it, lists the
# files and prints their layout (shapefile fields and first records, projection, bounding box) so it can be checked before a loader is written.
# Run by you. An existing download is kept; delete the folder to fetch it again.
# Usage: download-catchments.sh [dest_dir]   (default: data/raw/catchments)
# A download needs no database, so this does not source common.sh (and its pipeline/.env).
set -euo pipefail
PIPELINE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RAW_DIR="${RAW_DIR:-$PIPELINE_DIR/../data/raw}"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/catchments.env"; set +a

DEST="${1:-$RAW_DIR/catchments}"
ZIP="$DEST/catchments.zip"
USER_AGENT="HomeFinder-personal-research/0.1"
mkdir -p "$DEST"

if [[ -f "$ZIP" ]]; then
  echo "catchments: already downloaded ($ZIP)"
else
  curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$ZIP.part" "$CATCHMENTS_URL"
  # A zip starts with the bytes "PK"; an error page or a redirect to a web page does not.
  [[ "$(head -c 2 "$ZIP.part")" == "PK" ]] || { echo "Not a zip file; check CATCHMENTS_URL in config/catchments.env (see the Data NSW page)" >&2; rm -f "$ZIP.part"; exit 1; }
  unzip -tq "$ZIP.part" >/dev/null || { echo "The download is damaged (zip test failed); try again" >&2; rm -f "$ZIP.part"; exit 1; }
  mv "$ZIP.part" "$ZIP"
  sha256sum "$ZIP" | cut -d' ' -f1 > "$ZIP.sha256"
  echo "catchments: downloaded $(du -h "$ZIP" | cut -f1)"
fi

if ! find "$DEST" -type f ! -name 'catchments.zip*' | grep -q .; then
  unzip -q -o "$ZIP" -d "$DEST"
fi
echo "catchments: files under $DEST:"
find "$DEST" -type f ! -name 'catchments.zip*' | sort | while read -r f; do
  echo "  $f ($(du -h "$f" | cut -f1))"
done
echo "catchments: layout (paste this back):"
python3 -I "$PIPELINE_DIR/scripts/describe-gis-files.py" "$DEST"
