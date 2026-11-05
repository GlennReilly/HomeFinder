#!/usr/bin/env bash
# Loads each suburb's SEIFA 2021 socio-economic advantage (IRSAD decile and score) into `suburb_seifa` (ADR 0049). The Kotlin tool in the
# `census` module (run through Gradle, so a JDK 17 is needed) reads the ABS workbook; this script loads its CSV and matches the ABS suburbs
# to ours. Idempotent. Needs load-suburbs.sh (and load-lga.sh to tell shared names apart).
# Usage: load-seifa.sh [workbook.xlsx]   (default: data/raw/seifa/seifa-2021-sal.xlsx, made by download-seifa.sh)
source "$(dirname "$0")/common.sh"
ensure_migrated

FILE="${1:-$RAW_DIR/seifa/seifa-2021-sal.xlsx}"
[[ -f "$FILE" ]] || { echo "Missing $FILE; run download-seifa.sh" >&2; exit 1; }
FILE="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"
[[ "$FILE" != *" "* ]] || { echo "The workbook path must not contain spaces: $FILE" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
(cd "$PIPELINE_DIR/.." && ./gradlew --console=plain --quiet :census:seifa --no-configuration-cache --args="$FILE $WORK/seifa.csv")
[[ -s "$WORK/seifa.csv" ]] || { echo "The SEIFA tool wrote no results" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.seifa_raw" -c "CREATE TABLE stg.seifa_raw (sal_code text, sal_name text, irsad_score double precision, irsad_decile smallint, population int)"
psql_db -q -c "\copy stg.seifa_raw from stdin with (format csv, header)" < "$WORK/seifa.csv"
psql_db -q < "$PIPELINE_DIR/sql/seifa.sql"
psql_db -q -c "DROP TABLE IF EXISTS stg.seifa_raw"

echo "SEIFA loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' of ' || (SELECT count(*) FROM suburb) || ' suburbs have a SEIFA decile' FROM suburb_seifa"
UNMATCHED="$(psql_db -t -A -c "SELECT string_agg(name, ', ' ORDER BY name) FROM (SELECT s.name FROM suburb s WHERE NOT EXISTS (SELECT 1 FROM suburb_seifa c WHERE c.suburb_id = s.suburb_id) ORDER BY s.name LIMIT 15) x")"
[[ -z "$UNMATCHED" ]] || echo "Not matched (no ABS suburb of that name with a score, or the name is shared and the council area did not settle it): $UNMATCHED"
