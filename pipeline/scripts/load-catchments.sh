#!/usr/bin/env bash
# Loads the current NSW public school catchments (intake areas) into `school_catchment` (ADR 0056). The Kotlin tool in the `census` module (run
# through Gradle, so a JDK 17 is needed) reads the Department of Education's primary and secondary shapefiles (the file of future catchments is
# ignored) and writes a CSV of zones with their shapes; this script loads it and reprojects the shapes from GDA94 to GDA2020. Idempotent. The
# schools themselves come from load-schools.sh; the two are matched by school code when a card is shown, so either may be loaded first.
# Usage: load-catchments.sh [dir]   (default: data/raw/catchments, made by download-catchments.sh)
source "$(dirname "$0")/common.sh"
ensure_migrated

DIR="${1:-$RAW_DIR/catchments}"
[[ -d "$DIR" ]] || { echo "Missing $DIR; run download-catchments.sh" >&2; exit 1; }
DIR="$(cd "$DIR" && pwd)"
[[ "$DIR" != *" "* ]] || { echo "The catchments directory path must not contain spaces: $DIR" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
(cd "$PIPELINE_DIR/.." && ./gradlew --console=plain --quiet :census:catchments --no-configuration-cache --args="$DIR $WORK/catchments.csv")
[[ -s "$WORK/catchments.csv" ]] || { echo "The catchments tool wrote no results" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.catchment_raw" -c "CREATE TABLE stg.catchment_raw (level text, school_code int, catch_type text, name text, added_on text, years text, enrolment_year int, wkt text)"
psql_db -q -c "\copy stg.catchment_raw from stdin with (format csv, header)" < "$WORK/catchments.csv"
psql_db -q < "$PIPELINE_DIR/sql/catchments.sql"
TOTAL="$(psql_db -t -A -c "SELECT count(*) FROM stg.catchment_raw")"
psql_db -q -c "DROP TABLE IF EXISTS stg.catchment_raw"

echo "Catchments loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) FILTER (WHERE level = 'PRIMARY') || ' primary and ' || count(*) FILTER (WHERE level = 'HIGH') || ' high school catchments, enrolment year ' || coalesce(max(enrolment_year)::text, 'unknown') FROM school_catchment"
LOADED="$(psql_db -t -A -c "SELECT count(*) FROM school_catchment")"
[[ "$LOADED" == "$TOTAL" ]] || echo "$((TOTAL - LOADED)) of $TOTAL catchments had no usable shape and were dropped"
if [[ "$(psql_db -t -A -c "SELECT count(*) FROM school")" -gt 0 ]]; then
  psql_db -t -A -c "SELECT count(DISTINCT c.school_code) FILTER (WHERE s.school_code IS NOT NULL) || ' of ' || count(DISTINCT c.school_code) || ' catchment schools match a school in the schools data (the cards use its full name and ICSEA)' FROM school_catchment c LEFT JOIN school s ON s.school_code = c.school_code"
  UNMATCHED="$(psql_db -t -A -c "SELECT string_agg(name, ', ' ORDER BY name) FROM (SELECT DISTINCT c.name FROM school_catchment c WHERE NOT EXISTS (SELECT 1 FROM school s WHERE s.school_code = c.school_code) ORDER BY c.name LIMIT 15) x")"
  [[ -z "$UNMATCHED" ]] || echo "No school row for (card shows the Department's short name): $UNMATCHED"
else
  echo "No schools loaded yet (load-schools.sh); the cards will use the Department's short school names until then"
fi
