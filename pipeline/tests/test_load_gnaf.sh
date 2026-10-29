#!/usr/bin/env bash
# Integration test: loads the fixture G-NAF extract into a throwaway PostGIS and checks the filtering rules.
# Requires Docker. Usage: tests/test_load_gnaf.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'ENV_FILE="$WORK/test.env" docker compose -p hf-gnaf-test -f "$HERE/../docker-compose.yml" --env-file "$WORK/test.env" down -v >/dev/null 2>&1; rm -rf "$WORK"' EXIT

cat > "$WORK/test.env" <<ENV
POSTGRES_DB=homefinder
POSTGRES_USER=homefinder
POSTGRES_PASSWORD=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
POSTGRES_PORT=${TEST_POSTGRES_PORT:-55433}
ENV
export ENV_FILE="$WORK/test.env" COMPOSE_PROJECT_NAME=hf-gnaf-test RAW_DIR="$WORK/raw"

docker compose -p hf-gnaf-test -f "$HERE/../docker-compose.yml" --env-file "$ENV_FILE" up -d --wait --wait-timeout 120 db
docker compose -p hf-gnaf-test -f "$HERE/../docker-compose.yml" --env-file "$ENV_FILE" run --rm migrate

# shellcheck disable=SC1091
source "$HERE/../scripts/common.sh"
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null
first="$(psql_db -t -A -c "SELECT string_agg(gnaf_pid, ',' ORDER BY gnaf_pid) FROM address")"
"$HERE/../scripts/load-gnaf.sh" "$HERE/fixtures/gnaf" >/dev/null   # rerun must be idempotent
second="$(psql_db -t -A -c "SELECT string_agg(gnaf_pid, ',' ORDER BY gnaf_pid) FROM address")"

fail() { echo "FAIL: $1" >&2; exit 1; }
[[ "$first" == "GANSW1,GANSW5" ]] || fail "expected GANSW1,GANSW5 but got '$first'"
[[ "$first" == "$second" ]] || fail "rerun changed output: '$second'"
srid="$(psql_db -t -A -c "SELECT ST_SRID(geom) FROM address LIMIT 1")"
[[ "$srid" == "7844" ]] || fail "expected SRID 7844 but got $srid"
runs="$(psql_db -t -A -c "SELECT count(*) FROM pipeline_run WHERE stage='gnaf_address' AND input_versions->>'gnaf_release'='$GNAF_RELEASE'")"
[[ "$runs" == "2" ]] || fail "expected 2 pipeline_run rows but got $runs"
echo "PASS"
