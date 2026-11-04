#!/usr/bin/env bash
# Works out each lot's height above its nearest drainage line from the 5 m DEM tiles and loads it into `lot_drainage` (ADR 0040).
# It exports each lot's representative point, runs the Kotlin tool in the `drainage` module (through Gradle, so a JDK 17 is needed),
# and loads its CSV. Idempotent. Needs load-lots.sh and the GeoTIFFs from download-dem.sh.
# Usage: load-drainage.sh [dem dir] [stream catchment in hectares]   (defaults: data/raw/dem, DRAINAGE_STREAM_HA from config/drainage.env)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/drainage.env"; set +a
ensure_migrated

DIR="${1:-$RAW_DIR/dem}"
STREAM_HA="${2:-$DRAINAGE_STREAM_HA}"
[[ -d "$DIR" ]] || { echo "Missing $DIR; run download-dem.sh" >&2; exit 1; }
DIR="$(cd "$DIR" && pwd)"
[[ "$DIR" != *" "* ]] || { echo "The DEM directory path must not contain spaces: $DIR" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM lot")" -gt 0 ]] || { echo "No lots loaded; run load-lots.sh" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
psql_db -q -c "\copy (SELECT lot_id, ST_X(p) AS longitude, ST_Y(p) AS latitude FROM (SELECT lot_id, ST_PointOnSurface(geom) AS p FROM lot) x ORDER BY lot_id) to stdout with (format csv, header)" > "$WORK/points.csv"

(cd "$PIPELINE_DIR/.." && ./gradlew --console=plain --quiet :drainage:run --no-configuration-cache --args="$DIR $WORK/points.csv $WORK/result.csv $STREAM_HA")
[[ -s "$WORK/result.csv" ]] || { echo "The drainage tool wrote no results" >&2; exit 1; }

psql_db -q -c "DROP TABLE IF EXISTS stg.drainage_raw" -c "CREATE TABLE stg.drainage_raw (lot_id text, height_above_drainage_m real, stream_catchment_ha real)"
psql_db -q -c "\copy stg.drainage_raw from stdin with (format csv, header)" < "$WORK/result.csv"
psql_db -q < "$PIPELINE_DIR/sql/drainage.sql"
psql_db -q -c "DROP TABLE IF EXISTS stg.drainage_raw"

echo "Drainage loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' of ' || (SELECT count(*) FROM lot) || ' lots have a height above drainage; ' || count(*) FILTER (WHERE height_above_drainage_m < 1) || ' within 1 m of a stream, ' || count(*) FILTER (WHERE height_above_drainage_m >= 5) || ' at least 5 m above one' FROM lot_drainage"
