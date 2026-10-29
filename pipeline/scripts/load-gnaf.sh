#!/usr/bin/env bash
# Loads NSW G-NAF PSV tables into staging, then rebuilds `address` for the pilot localities.
# Usage: load-gnaf.sh [gnaf_dir]   (default: the unzipped pinned release under data/raw)
source "$(dirname "$0")/common.sh"
ensure_migrated

GNAF_DIR="${1:-$RAW_DIR/gnaf/$GNAF_RELEASE/unzipped}"
SHA_FILE="$RAW_DIR/gnaf/$GNAF_RELEASE/$GNAF_ZIP_NAME.sha256"
ZIP_SHA="$( [[ -f "$SHA_FILE" ]] && cat "$SHA_FILE" || echo unknown )"
TABLES=(STATE LOCALITY STREET_LOCALITY ADDRESS_DETAIL ADDRESS_DEFAULT_GEOCODE)

psql_db -q -c "TRUNCATE pilot_locality"
psql_db -q -c "\copy pilot_locality (name, postcode, expect_addresses) from stdin with (format csv, header)" \
  < "$PIPELINE_DIR/config/pilot_localities.csv"

for table in "${TABLES[@]}"; do
  file="$(find "$GNAF_DIR" -name "NSW_${table}_psv.psv" -print -quit)"
  [[ -n "$file" ]] || { echo "Missing NSW_${table}_psv.psv under $GNAF_DIR" >&2; exit 1; }
  # Staging columns come from the file header, all TEXT, so a schema change fails later in SQL, not silently here.
  cols="$(head -n 1 "$file" | tr -d '\r' | tr 'A-Z' 'a-z')"
  ddl="$(echo "$cols" | sed 's/|/ text, /g; s/$/ text/')"
  collist="$(echo "$cols" | tr '|' ',')"
  stg="stg.$(echo "$table" | tr 'A-Z' 'a-z')"
  psql_db -q -c "DROP TABLE IF EXISTS $stg" -c "CREATE TABLE $stg ($ddl)"
  # quote char \x01 effectively disables CSV quoting, as PSV files are unquoted.
  psql_db -q -c "\copy $stg ($collist) from stdin with (format csv, delimiter '|', header, quote E'\\x01')" < "$file"
  # Fresh tables have no statistics; without this the planner can pick a very slow join order.
  psql_db -q -c "ANALYZE $stg"
done

psql_db -q -v release="$GNAF_RELEASE" -v zip_sha256="$ZIP_SHA" < "$PIPELINE_DIR/sql/gnaf_address.sql"
psql_db -t -c "SELECT count(*) || ' addresses loaded' FROM address"
"$(dirname "$0")/check-gnaf.sh"
