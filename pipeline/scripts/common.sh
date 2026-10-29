# Shared setup for pipeline scripts. Source it; do not execute.
set -euo pipefail

PIPELINE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$PIPELINE_DIR/.env}"
RAW_DIR="${RAW_DIR:-$PIPELINE_DIR/../data/raw}"

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
# shellcheck disable=SC1091
. "$PIPELINE_DIR/config/gnaf-release.env"
set +a

compose() {
  docker compose -f "$PIPELINE_DIR/docker-compose.yml" --env-file "$ENV_FILE" "$@"
}

psql_db() {
  compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 "$@"
}

# Applies pending Flyway migrations (a no-op when up to date) so scripts never run against an old schema.
ensure_migrated() {
  compose up -d --wait db >/dev/null
  compose run --rm migrate >/dev/null
}

# Loads the committed pilot locality list (idempotent). Needed by both the suburb and the G-NAF loads.
load_pilot_localities() {
  psql_db -q -c "TRUNCATE pilot_locality"
  psql_db -q -c "\copy pilot_locality (name, postcode, expect_addresses) from stdin with (format csv, header)" \
    < "$PIPELINE_DIR/config/pilot_localities.csv"
}
