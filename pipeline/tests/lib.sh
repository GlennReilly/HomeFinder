# Shared test setup: starts a throwaway PostGIS (own compose project, random password) and applies migrations.
# Source it; it defines HERE, fail() and the pipeline script helpers, and cleans up on exit.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
PROJECT="${TEST_PROJECT:-hf-pipeline-test}"
WORK="$(mktemp -d)"
COMPOSE=(docker compose -p "$PROJECT" -f "$HERE/../docker-compose.yml" --env-file "$WORK/test.env")
trap '"${COMPOSE[@]}" down -v >/dev/null 2>&1; rm -rf "$WORK"' EXIT

cat > "$WORK/test.env" <<ENV
POSTGRES_DB=homefinder
POSTGRES_USER=homefinder
POSTGRES_PASSWORD=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
POSTGRES_PORT=${TEST_POSTGRES_PORT:-55433}
ENV
export CHECK_EMPTY_LOCALITIES=warn CHECK_MISSING_SUBURBS=warn ENV_FILE="$WORK/test.env" \
  COMPOSE_PROJECT_NAME="$PROJECT" RAW_DIR="$WORK/raw"

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 db
"${COMPOSE[@]}" run --rm migrate

# shellcheck disable=SC1091
source "$HERE/../scripts/common.sh"

fail() { echo "FAIL: $1" >&2; exit 1; }
