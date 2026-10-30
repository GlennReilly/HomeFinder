#!/usr/bin/env bash
# Loads the Overpass answers (beach, coast, park, shop JSON) into osm_feature, then rebuilds the nearest beach, parks and
# shops and the park area for every lot. Idempotent. Needs load-suburbs.sh and load-lots.sh.
# Usage: load-osm.sh [dir]   (default: data/raw/osm)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/osm.env"; set +a
ensure_migrated

DIR="${1:-$RAW_DIR/osm}"
for name in beach coast park shop; do
  [[ -f "$DIR/$name.json" ]] || { echo "Missing $DIR/$name.json; run download-osm.sh" >&2; exit 1; }
done
[[ "$(psql_db -t -A -c "SELECT count(*) FROM lot")" -gt 0 ]] || { echo "No lots loaded; run load-lots.sh" >&2; exit 1; }

# One tab-separated row per OSM element: type, id, kind, name, geometry as GeoJSON. @tsv escapes tabs, newlines and
# backslashes the way COPY's text format expects. A relation becomes a collection of its member lines, joined into
# areas later in SQL.
JQ_PROGRAM='
def ring: [.[] | [.lon, .lat]];
def kind:
  if .tags.natural == "beach" then "beach"
  elif .tags.natural == "coastline" then "coastline"
  elif (.tags.leisure == "park" or .tags.landuse == "recreation_ground") then "park"
  elif .tags.shop == "supermarket" then "supermarket"
  elif .tags.shop == "mall" then "mall"
  else empty end;
def geom:
  if .type == "node" then {type: "Point", coordinates: [.lon, .lat]}
  elif .type == "way" then
    (.geometry | ring) as $r
    | if ($r | length) > 3 and $r[0] == $r[-1] then {type: "Polygon", coordinates: [$r]} else {type: "LineString", coordinates: $r} end
  else {type: "GeometryCollection", geometries: [.members[]? | select(.type == "way" and .geometry) | {type: "LineString", coordinates: (.geometry | ring)}]} end;
.elements[] | select(.type == "node" or (.geometry // .members) != null) | (kind) as $k
| [.type, .id, $k, (.tags.name // ""), (geom | tojson)] | @tsv'

psql_db -q -c "DROP TABLE IF EXISTS stg.osm_raw" \
  -c "CREATE TABLE stg.osm_raw (osm_type text, osm_id bigint, kind text, name text, geojson text)"
for name in beach coast park shop; do
  jq -r "$JQ_PROGRAM" "$DIR/$name.json"
done | psql_db -q -c "\copy stg.osm_raw from stdin"

OSM_BASE="$(for name in beach coast park shop; do jq -r '.osm3s.timestamp_osm_base // empty' "$DIR/$name.json"; done | sort | head -n 1)"
psql_db -q -v min_park_m2="$MIN_PARK_AREA_M2" -v beach_coast_m="$BEACH_MAX_COAST_M" -v osm_base="${OSM_BASE:-}" \
  < "$PIPELINE_DIR/sql/osm.sql"
psql_db -q -c "SELECT refresh_lot_proximity($NEAREST_PARKS, $PARK_RADIUS_M)" >/dev/null

echo "OSM features loaded:"
psql_db -t -A -F ' | ' -c "SELECT kind, count(*) FROM osm_feature GROUP BY kind ORDER BY kind"
psql_db -t -A -F ' | ' -c "SELECT 'lots with a beach/park/supermarket/mall: ' || count(DISTINCT lot_id) FILTER (WHERE kind='beach') || '/' || count(DISTINCT lot_id) FILTER (WHERE kind='park') || '/' || count(DISTINCT lot_id) FILTER (WHERE kind='supermarket') || '/' || count(DISTINCT lot_id) FILTER (WHERE kind='mall') || ' of ' || (SELECT count(*) FROM lot) FROM lot_nearest"
