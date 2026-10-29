#!/usr/bin/env bash
# Pages the NSW cadastre Lot layer around each pilot suburb, then rebuilds `lot` (lots whose representative point
# lies inside a pilot suburb). Requires load-suburbs.sh to have run.
# Usage: load-lots.sh [pages_dir]   (default: data/raw/cadastre/lots; existing page files are reused)
source "$(dirname "$0")/common.sh"
ensure_migrated

LOT_URL="https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Land_Parcel_Property_Theme/FeatureServer/8/query"
LOT_FIELDS="cadid,lotidstring,planlabel,lotnumber,sectionnumber,classsubtype,itstitlestatus"
PAGE_SIZE=2000
DIR="${1:-$RAW_DIR/cadastre/lots}"
mkdir -p "$DIR"

[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

# One envelope query per suburb keeps requests small; lots shared by neighbouring envelopes are deduplicated on load.
fetch_suburb() {
  local id="$1" env="$2" offset=0 file count
  while :; do
    file="$DIR/${id}_${offset}.geojson"
    if [[ ! -f "$file" ]]; then
      curl --fail --location --retry 3 --silent --show-error --output "$file.part" \
        --data-urlencode "where=1=1" --data-urlencode "geometry=$env" \
        --data-urlencode "geometryType=esriGeometryEnvelope" --data-urlencode "inSR=7844" \
        --data-urlencode "spatialRel=esriSpatialRelIntersects" --data-urlencode "outFields=$LOT_FIELDS" \
        --data-urlencode "outSR=7844" --data-urlencode "orderByFields=objectid" --data-urlencode "f=geojson" \
        --data-urlencode "resultOffset=$offset" --data-urlencode "resultRecordCount=$PAGE_SIZE" "$LOT_URL"
      jq -e '.features' "$file.part" >/dev/null || { echo "Bad response for suburb $id" >&2; rm -f "$file.part"; exit 1; }
      mv "$file.part" "$file"
    fi
    count="$(jq '.features | length' "$file")"
    [[ "$count" -lt "$PAGE_SIZE" ]] && break
    offset=$((offset + PAGE_SIZE))
  done
}

while IFS='|' read -r id env; do
  fetch_suburb "$id" "$env"
done < <(psql_db -t -A -F '|' -c "SELECT suburb_id, json_build_object('xmin', ST_XMin(e), 'ymin', ST_YMin(e), 'xmax', ST_XMax(e), 'ymax', ST_YMax(e), 'spatialReference', json_build_object('wkid', 7844))::text FROM (SELECT suburb_id, ST_Envelope(geom) AS e FROM suburb) s ORDER BY suburb_id")

psql_db -q -c "DROP TABLE IF EXISTS stg.lot_json" -c "CREATE TABLE stg.lot_json (doc text)"
find "$DIR" -name '*.geojson' -print0 | xargs -0 jq -c '.features[]' \
  | psql_db -q -c "\copy stg.lot_json from stdin with (format csv, delimiter E'\\x02', quote E'\\x01')"
psql_db -q -c "ANALYZE stg.lot_json" < /dev/null
psql_db -q < "$PIPELINE_DIR/sql/lot.sql"
psql_db -t -A -c "SELECT count(*) || ' lots loaded' FROM lot"
