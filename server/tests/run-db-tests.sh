#!/usr/bin/env bash
# Runs the PostGIS integration tests (tile SQL and suburb lookups) against a throwaway, migrated database (needs Docker).
# Usage: server/tests/run-db-tests.sh
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT=hf-server-db-test
PORT="${TEST_POSTGRES_PORT:-55436}"
WORK="$(mktemp -d)"
PASSWORD="$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')"
COMPOSE=(docker compose -p "$PROJECT" -f "$REPO/pipeline/docker-compose.yml" --env-file "$WORK/test.env")
trap '"${COMPOSE[@]}" down -v >/dev/null 2>&1; rm -rf "$WORK"' EXIT

cat > "$WORK/test.env" <<ENV
POSTGRES_DB=homefinder
POSTGRES_USER=homefinder
POSTGRES_PASSWORD=$PASSWORD
POSTGRES_PORT=$PORT
ENV

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 db
"${COMPOSE[@]}" run --rm migrate

export HOMEFINDER_TEST_DB_URL="jdbc:postgresql://127.0.0.1:$PORT/homefinder"
export HOMEFINDER_TEST_DB_USER=homefinder
export HOMEFINDER_TEST_DB_PASSWORD="$PASSWORD"
cd "$REPO"
./gradlew :server:test --tests '*IntegrationTest*' --rerun-tasks
