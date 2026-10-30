#!/usr/bin/env bash
# Loads the NSW public schools master dataset into `school` and finds, for each school, the pilot suburb that contains it.
# Idempotent. Needs load-suburbs.sh. Usage: load-schools.sh [file]   (default: data/raw/schools/master_dataset.csv)
source "$(dirname "$0")/common.sh"
# shellcheck disable=SC1091
set -a; . "$PIPELINE_DIR/config/schools.env"; set +a
ensure_migrated

FILE="${1:-$RAW_DIR/schools/master_dataset.csv}"
[[ -f "$FILE" ]] || { echo "Missing $FILE; run download-schools.sh" >&2; exit 1; }
[[ "$(psql_db -t -A -c "SELECT count(*) FROM suburb")" -gt 0 ]] || { echo "No suburbs loaded; run load-suburbs.sh" >&2; exit 1; }

# The staging table takes its columns from the file's own header line (minus a byte order mark and a carriage return), so
# a reordered or extended file still loads; the columns the loader needs are checked by name below.
HEADER="$(head -n 1 "$FILE" | tr -d '\r' | sed '1s/^\xEF\xBB\xBF//')"
IFS=',' read -r -a NAMES <<< "$HEADER"
for needed in School_code School_name Town_suburb Postcode Level_of_schooling Selective_school ICSEA_value latest_year_enrolment_FTE Website Latitude Longitude Date_extracted; do
  printf '%s\n' "${NAMES[@]}" | grep -qx "$needed" || { echo "The file has no $needed column; the dataset has changed" >&2; exit 1; }
done
COLUMNS=""
for name in "${NAMES[@]}"; do COLUMNS+="\"${name//\"/\"\"}\" text, "; done
COLUMNS="${COLUMNS%, }"

psql_db -q -c "DROP TABLE IF EXISTS stg.school_raw" -c "CREATE TABLE stg.school_raw ($COLUMNS)"
psql_db -q -c "\copy stg.school_raw from stdin with (format csv, header)" < "$FILE"
psql_db -q -v min_icsea="$MIN_ICSEA" -v max_icsea="$MAX_ICSEA" < "$PIPELINE_DIR/sql/school.sql"

echo "Schools loaded:"
psql_db -t -A -F ' | ' -c "SELECT count(*) || ' schools, ' || count(icsea) || ' with an ICSEA, ' || count(suburb_id) || ' inside a pilot suburb, data extracted ' || COALESCE(max(extracted_on)::text, 'n/a') FROM school"
