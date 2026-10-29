#!/usr/bin/env bash
# Loads the Newcastle and Lake Macquarie LGA polygons (GDA2020, GeoJSON) from NSW Spatial Services.
# Usage: load-lga.sh [geojson]   (default: download to data/raw/lga/lga.geojson if absent)
source "$(dirname "$0")/common.sh"
ensure_migrated

LGA_URL="https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Administrative_Boundaries_Theme/FeatureServer/8/query?where=lganame%20in%20(%27NEWCASTLE%27,%27LAKE%20MACQUARIE%27)&outFields=lganame&outSR=7844&f=geojson"
FILE="${1:-$RAW_DIR/lga/lga.geojson}"

if [[ ! -f "$FILE" ]]; then
  mkdir -p "$(dirname "$FILE")"
  curl --fail --location --output "$FILE" "$LGA_URL"
fi

psql_db -q -c "DROP TABLE IF EXISTS stg.lga_json" -c "CREATE TABLE stg.lga_json (doc text)"
# GeoJSON is one line; quote and delimiter bytes that cannot occur in it keep it as a single field.
tr -d '\n\r' < "$FILE" | psql_db -q -c "\copy stg.lga_json from stdin with (format csv, delimiter E'\\x02', quote E'\\x01')"
psql_db -q <<'SQL'
BEGIN;
TRUNCATE lga;
INSERT INTO lga (name, geom)
SELECT f -> 'properties' ->> 'lganame',
       ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON((f -> 'geometry')::text), 7844))
FROM stg.lga_json, jsonb_array_elements(doc::jsonb -> 'features') AS f;
COMMIT;
SQL
psql_db -t -A -c "SELECT count(*) || ' LGA polygons loaded' FROM lga"
