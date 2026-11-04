#!/usr/bin/env bash
# Downloads the ABS 2021 Census General Community Profile for Suburbs and Localities, NSW (a zip of about 34 MB) and unpacks it into
# data/raw/census. Run by you. An existing download is kept; delete the folder to fetch it again.
# Usage: download-census.sh [dest_dir]   (default: data/raw/census)
# A download needs no database, so this does not source common.sh (and its pipeline/.env).
set -euo pipefail
PIPELINE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RAW_DIR="${RAW_DIR:-$PIPELINE_DIR/../data/raw}"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/census.env"; set +a

DEST="${1:-$RAW_DIR/census}"
ZIP="$DEST/census_sal_nsw.zip"
USER_AGENT="HomeFinder-personal-research/0.1"
mkdir -p "$DEST"

if [[ -f "$ZIP" ]]; then
  echo "census: already downloaded ($ZIP)"
else
  curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$ZIP.part" "$CENSUS_URL"
  # A zip starts with the bytes "PK"; an error page or a redirect to a web page does not.
  [[ "$(head -c 2 "$ZIP.part")" == "PK" ]] || { echo "Not a zip file; check CENSUS_URL in config/census.env (see the ABS DataPacks page)" >&2; rm -f "$ZIP.part"; exit 1; }
  unzip -tq "$ZIP.part" >/dev/null || { echo "The download is damaged (zip test failed); try again" >&2; rm -f "$ZIP.part"; exit 1; }
  mv "$ZIP.part" "$ZIP"
  sha256sum "$ZIP" | cut -d' ' -f1 > "$ZIP.sha256"
  echo "census: downloaded $(du -h "$ZIP" | cut -f1)"
fi

# Unpack beside the zip (flat is not assumed: the tables stay in whatever folders the ABS used).
if ! find "$DEST" -type f -iname '*.csv' | grep -q .; then
  unzip -q -o "$ZIP" -d "$DEST"
fi
FILES="$(find "$DEST" -type f -iname '*.csv' | wc -l)"
echo "census: $FILES CSV files under $DEST"
if find "$DEST" -type f -iname "*${CENSUS_TABLE}*" | grep -q .; then
  echo "census: found the $CENSUS_TABLE table files:"
  find "$DEST" -type f -iname "*${CENSUS_TABLE}*" | sort | sed 's/^/  /'
else
  echo "census: no file name contains $CENSUS_TABLE; the loader needs the tenure by dwelling structure table (see the zip's metadata)" >&2
  exit 1
fi
