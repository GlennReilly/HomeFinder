#!/usr/bin/env bash
# Rebuilds the precomputed suburb tile data (simplified geometry per zoom, label points) from the loaded suburbs.
# load-suburbs.sh does this automatically; run this after a migration or to refresh without reloading suburbs.
source "$(dirname "$0")/common.sh"
ensure_migrated

psql_db -q -c "SELECT refresh_suburb_tile_data()" > /dev/null
psql_db -t -A -c "SELECT (SELECT count(*) FROM suburb_tile_geometry) || ' tile geometries and ' || (SELECT count(*) FROM suburb_label) || ' labels'"
