#!/usr/bin/env bash
# Downloads the NSW public schools master dataset (one CSV, about 1.3 MB) into data/raw/schools. Run by you. An existing file is
# kept; delete it to fetch the current one (the Department updates it every night, and ICSEA once a year).
# Usage: download-schools.sh [dest_dir]   (default: data/raw/schools)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/schools.env"; set +a

DEST="${1:-$RAW_DIR/schools}"
FILE="$DEST/master_dataset.csv"
USER_AGENT="HomeFinder-personal-research/0.1 (glenn.reilly@gmail.com)"
mkdir -p "$DEST"

if [[ -f "$FILE" ]]; then
  echo "schools: already downloaded ($FILE)"
else
  curl --fail --location --silent --show-error --retry 3 --user-agent "$USER_AGENT" --output "$FILE.part" "$SCHOOLS_URL"
  head -n 1 "$FILE.part" | grep -q "School_code" || { echo "Not the schools dataset (no School_code column); check SCHOOLS_URL" >&2; rm -f "$FILE.part"; exit 1; }
  mv "$FILE.part" "$FILE"
  sha256sum "$FILE" | cut -d' ' -f1 > "$FILE.sha256"
  echo "schools: $(($(wc -l < "$FILE") - 1)) rows downloaded"
fi
