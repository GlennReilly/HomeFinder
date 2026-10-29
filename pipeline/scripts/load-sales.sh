#!/usr/bin/env bash
# Loads the B (sale) records of every *.DAT file under a directory for the configured districts, applies the counting
# rules (sql/sales.sql) and rebuilds suburb_stats. Idempotent. Requires load-suburbs.sh (and load-lots.sh for block sizes).
# Usage: load-sales.sh [dir]   (default: data/raw/sales)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/sales.env"; set +a
ensure_migrated

DIR="${1:-$RAW_DIR/sales}"
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }
[[ -n "$(find "$DIR" -iname '*.dat' -print -quit 2>/dev/null)" ]] || { echo "No .DAT files under $DIR; run download-sales.sh" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.sale_raw" -c "DROP TABLE IF EXISTS stg.sale_district" \
  -c "CREATE TABLE stg.sale_district (code text, name text)" \
  -c "CREATE TABLE stg.sale_raw ($(seq -s ', ' -f 'c%.0f text' 1 25), source_file text)"
psql_db -q -c "\copy stg.sale_district from stdin with (format csv, header)" < "$PIPELINE_DIR/config/sales_districts.csv"

# Keep only B records; strip CRLF; append the file name as the last column (the trailing ';' makes c25 empty).
while IFS= read -r -d '' file; do
  awk -v f="$(basename "$file")" '{ sub(/\r$/, "") } /^B;/ { print $0 ";" f }' "$file"
done < <(find "$DIR" -iname '*.dat' -print0 | sort -z) \
  | psql_db -q -c "\copy stg.sale_raw from stdin with (format csv, delimiter ';', quote E'\\x01')"

psql_db -q -v min_price="$MIN_SALE_PRICE_AUD" < "$PIPELINE_DIR/sql/sales.sql"
"$(dirname "$0")/refresh-suburb-stats.sh" >/dev/null

echo "Sales loaded ($(psql_db -t -A -c "SELECT count(*) FROM sale") rows); counted vs excluded:"
psql_db -t -A -F ' | ' -c "SELECT COALESCE(exclusion_reason, 'counted'), count(*) FROM sale GROUP BY 1 ORDER BY 1"
"$(dirname "$0")/refresh-suburb-stats.sh"
