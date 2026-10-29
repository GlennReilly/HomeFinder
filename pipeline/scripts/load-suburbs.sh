#!/usr/bin/env bash
# Loads official NSW suburb boundaries for the pilot localities (GDA2020) from NSW Spatial Services.
# Usage: load-suburbs.sh [geojson]   (default: download to data/raw/cadastre/suburbs.geojson if absent)
# Rebuilds `suburb`; this also clears `lot` and `address_lot`, so rerun load-lots.sh and link-address-lots.sh.
source "$(dirname "$0")/common.sh"
ensure_migrated

SUBURB_URL="https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Administrative_Boundaries_Theme/FeatureServer/2/query"
FILE="${1:-$RAW_DIR/cadastre/suburbs.geojson}"

if [[ ! -f "$FILE" ]]; then
  mkdir -p "$(dirname "$FILE")"
  names="$(psql_db -t -A -c "SELECT string_agg(quote_literal(name), ',') FROM pilot_locality")"
  curl --fail --location --retry 3 --output "$FILE" \
    --data-urlencode "where=suburbname in ($names)" \
    --data-urlencode "outFields=cadid,suburbname,postcode" \
    --data-urlencode "outSR=7844" --data-urlencode "f=geojson" --data-urlencode "resultRecordCount=2000" \
    "$SUBURB_URL"
fi

psql_db -q -c "DROP TABLE IF EXISTS stg.suburb_json" -c "CREATE TABLE stg.suburb_json (doc text)"
jq -c '.features[]' "$FILE" \
  | psql_db -q -c "\copy stg.suburb_json from stdin with (format csv, delimiter E'\\x02', quote E'\\x01')"
psql_db -q < "$PIPELINE_DIR/sql/suburb.sql"
psql_db -t -A -c "SELECT count(*) || ' suburbs loaded' FROM suburb"

# Every pilot locality should have an official boundary; a missing one means a name mismatch.
missing="$(psql_db -t -A -c "SELECT name FROM pilot_locality p WHERE NOT EXISTS (SELECT 1 FROM suburb s WHERE upper(s.name) = p.name) ORDER BY 1")"
if [[ -n "$missing" ]]; then
  level="FAIL"; [[ "${CHECK_MISSING_SUBURBS:-fail}" == "warn" ]] && level="WARN"
  echo "$level: pilot localities without a suburb boundary:" >&2
  echo "$missing" >&2
  [[ "$level" == "FAIL" ]] && exit 1
fi
exit 0
