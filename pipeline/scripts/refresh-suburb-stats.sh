#!/usr/bin/env bash
# Rebuilds suburb_stats from the loaded sales and lots (idempotent). Needs load-sales.sh and load-lots.sh to have run.
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/sales.env"; set +a
ensure_migrated

psql_db -q -c "SELECT refresh_suburb_stats($MIN_SALES_FOR_MEDIAN, $MIN_LOTS_FOR_BLOCK_SIZE, $MIN_BLOCK_AREA_M2)" >/dev/null
psql_db -t -A -F ' | ' -c "
  SELECT 'as of ' || COALESCE(max(as_of)::text, 'n/a') || '; suburbs with a house median: ' || count(house_median_aud)
         || ', unit median: ' || count(unit_median_aud) || ', block size: ' || count(block_median_m2)
         || ' of ' || count(*) FROM suburb_stats"
