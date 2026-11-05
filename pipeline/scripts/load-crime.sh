#!/usr/bin/env bash
# Loads each suburb's recorded crime over the latest 24 months (violent and property incidents) from the BOCSAR suburb file into `suburb_crime`
# (ADR 0051). The Kotlin tool in the `census` module (run through Gradle, so a JDK 17 is needed) streams the 400 MB CSV; this script loads its
# small CSV and matches BOCSAR suburbs to ours. Idempotent. Needs load-suburbs.sh (and load-lga.sh to tell shared names apart); the rate per
# 1,000 residents also needs load-seifa.sh (its population), but the load does not.
# Usage: load-crime.sh [SuburbData.csv]   (default: data/raw/bocsar/SuburbData.csv, made by download-bocsar.sh)
source "$(dirname "$0")/common.sh"
ensure_migrated

FILE="${1:-$RAW_DIR/bocsar/SuburbData.csv}"
[[ -f "$FILE" ]] || { echo "Missing $FILE; run download-bocsar.sh" >&2; exit 1; }
FILE="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"
[[ "$FILE" != *" "* ]] || { echo "The file path must not contain spaces: $FILE" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
(cd "$PIPELINE_DIR/.." && ./gradlew --console=plain --quiet :census:crime --no-configuration-cache --args="$FILE $WORK/crime.csv")
[[ -s "$WORK/crime.csv" ]] || { echo "The crime tool wrote no results" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.crime_raw" -c "CREATE TABLE stg.crime_raw (bocsar_name text, violent int, property int, window_text text, as_of date)"
psql_db -q -c "\copy stg.crime_raw from stdin with (format csv, header)" < "$WORK/crime.csv"
psql_db -q < "$PIPELINE_DIR/sql/crime.sql"
psql_db -q -c "DROP TABLE IF EXISTS stg.crime_raw"

echo "Crime loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' of ' || (SELECT count(*) FROM suburb) || ' suburbs have BOCSAR crime counts (' || coalesce(min(window_text), 'none') || ')' FROM suburb_crime"
UNMATCHED="$(psql_db -t -A -c "SELECT string_agg(name, ', ' ORDER BY name) FROM (SELECT s.name FROM suburb s WHERE NOT EXISTS (SELECT 1 FROM suburb_crime c WHERE c.suburb_id = s.suburb_id) ORDER BY s.name LIMIT 15) x")"
[[ -z "$UNMATCHED" ]] || echo "Not matched (no BOCSAR suburb of that name, or the name is shared and the council area did not settle it): $UNMATCHED"
