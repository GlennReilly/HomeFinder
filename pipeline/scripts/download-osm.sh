#!/usr/bin/env bash
# Asks the public Overpass API for beaches, the coastline, parks and supermarkets and shopping centres around the
# loaded suburbs and saves each answer in data/raw/osm. Needs load-suburbs.sh to have run. Run by you: the answers are
# a few megabytes. Existing files are kept; delete one to fetch it again. Be kind to the shared service: one request at a time.
# Usage: download-osm.sh [dest_dir]   (default: data/raw/osm)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/osm.env"; set +a
ensure_migrated

DEST="${1:-$RAW_DIR/osm}"
USER_AGENT="HomeFinder-personal-research/0.1 (glenn.reilly@gmail.com)"
PAUSE_SECONDS=5
mkdir -p "$DEST"

[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

# south,west,north,east of the suburbs widened by a margin
box() {
  psql_db -t -A -F ',' -c "SELECT round((ST_YMin(e) - $1)::numeric, 4), round((ST_XMin(e) - $1)::numeric, 4), round((ST_YMax(e) + $1)::numeric, 4), round((ST_XMax(e) + $1)::numeric, 4) FROM (SELECT ST_Extent(geom) AS e FROM suburb) s"
}

fetch() {
  local name="$1" bbox="$2" body="$3" file="$DEST/$1.json"
  [[ -f "$file" ]] && { echo "$name: already downloaded"; return; }
  local query="[out:json][timeout:300];(${body//BBOX/$bbox});out geom tags;"
  curl --fail --location --silent --show-error --retry 5 --retry-delay 30 --retry-all-errors --user-agent "$USER_AGENT" \
    --data-urlencode "data=$query" --output "$file.part" "$OVERPASS_URL"
  jq -e '.elements' "$file.part" >/dev/null || { echo "Bad answer for $name" >&2; rm -f "$file.part"; exit 1; }
  mv "$file.part" "$file"
  echo "$name: $(jq '.elements | length' "$file") elements"
  sleep "$PAUSE_SECONDS"
}

NEAR="$(box "$OSM_MARGIN_DEG")"
FAR="$(box "$OSM_BEACH_MARGIN_DEG")"
fetch beach "$FAR" 'nwr["natural"="beach"](BBOX);'
fetch coast "$FAR" 'way["natural"="coastline"](BBOX);'
fetch park "$NEAR" 'nwr["leisure"="park"](BBOX);nwr["landuse"="recreation_ground"](BBOX);'
fetch shop "$NEAR" 'nwr["shop"~"^(supermarket|mall)$"](BBOX);'
echo "OSM files ready in $DEST"
