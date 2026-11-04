#!/usr/bin/env bash
# Loads each suburb's tenure and dwelling structure from the ABS 2021 Census (ADR 0044) into `suburb_census`. The Kotlin tool in the `census`
# module (run through Gradle, so a JDK 17 is needed) reads table G37 and the geography workbook from the unpacked DataPack; this script
# loads its CSV and matches Census suburbs to ours. Idempotent. Needs load-suburbs.sh (and load-lga.sh to tell shared names apart).
# Usage: load-census.sh [census dir]   (default: data/raw/census, made by download-census.sh)
source "$(dirname "$0")/common.sh"
ensure_migrated

DIR="${1:-$RAW_DIR/census}"
[[ -d "$DIR" ]] || { echo "Missing $DIR; run download-census.sh" >&2; exit 1; }
DIR="$(cd "$DIR" && pwd)"
[[ "$DIR" != *" "* ]] || { echo "The census directory path must not contain spaces: $DIR" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
(cd "$PIPELINE_DIR/.." && ./gradlew --console=plain --quiet :census:run --no-configuration-cache --args="$DIR $WORK/census.csv")
[[ -s "$WORK/census.csv" ]] || { echo "The census tool wrote no results" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.census_raw" -c "CREATE TABLE stg.census_raw (sal_code text, sal_name text, tenure_stated int, rented int, houses int, semis int, flats int, other_dwellings int)"
psql_db -q -c "\copy stg.census_raw from stdin with (format csv, header)" < "$WORK/census.csv"
psql_db -q < "$PIPELINE_DIR/sql/census.sql"
psql_db -q -c "DROP TABLE IF EXISTS stg.census_raw"

echo "Census loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' of ' || (SELECT count(*) FROM suburb) || ' suburbs have Census tenure and dwelling counts' FROM suburb_census"
UNMATCHED="$(psql_db -t -A -c "SELECT string_agg(name, ', ' ORDER BY name) FROM (SELECT s.name FROM suburb s WHERE NOT EXISTS (SELECT 1 FROM suburb_census c WHERE c.suburb_id = s.suburb_id) ORDER BY s.name LIMIT 15) x")"
[[ -z "$UNMATCHED" ]] || echo "Not matched (no Census suburb of that name, or the name is shared and the council area did not settle it): $UNMATCHED"
