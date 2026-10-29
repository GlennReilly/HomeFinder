#!/usr/bin/env bash
# Completeness checks after load-gnaf.sh. Exits non-zero if an address could have been missed.
#  1. Every current principal G-NAF address whose point is inside an LGA polygon has its locality in the pilot list.
#  2. Every locality in the pilot list has at least one loaded address (catches name mismatches).
#     CHECK_EMPTY_LOCALITIES=warn downgrades this to a warning. Localities known to have no addresses are
#     flagged expect_addresses=false in the CSV; they must still exist in G-NAF.
#  3. Reports (does not fail) current principal addresses in pilot localities that have no current geocode.
source "$(dirname "$0")/common.sh"

status=0
q() { psql_db -t -A -F ' | ' -c "$1"; }

[[ "$(q "SELECT count(*) FROM lga")" -gt 0 ]] || { echo "No LGA polygons loaded; run scripts/load-lga.sh" >&2; exit 1; }

outside="$(q "
SELECT upper(l.locality_name) || ' (' || count(*) || ' addresses)'
FROM stg.address_detail ad
JOIN stg.locality l ON l.locality_pid = ad.locality_pid
JOIN stg.state s ON s.state_pid = l.state_pid AND s.state_abbreviation = 'NSW'
JOIN stg.address_default_geocode g ON g.address_detail_pid = ad.address_detail_pid AND g.date_retired IS NULL
JOIN lga ON ST_Contains(lga.geom, ST_SetSRID(ST_MakePoint(g.longitude::float8, g.latitude::float8), 7844))
WHERE ad.date_retired IS NULL AND ad.alias_principal = 'P'
  AND NOT EXISTS (SELECT 1 FROM pilot_locality p WHERE p.name = upper(l.locality_name))
GROUP BY upper(l.locality_name) ORDER BY 1")"
if [[ -n "$outside" ]]; then
  echo "FAIL: addresses inside the LGAs belong to localities missing from pilot_localities.csv:" >&2
  echo "$outside" >&2
  status=1
fi

empty="$(q "
SELECT p.name FROM pilot_locality p
WHERE p.expect_addresses AND NOT EXISTS (SELECT 1 FROM address a WHERE upper(a.locality_name) = p.name) ORDER BY 1")"
if [[ -n "$empty" ]]; then
  level="FAIL"; [[ "${CHECK_EMPTY_LOCALITIES:-fail}" == "warn" ]] && level="WARN"
  echo "$level: pilot localities with no loaded address (check the spelling against G-NAF):" >&2
  echo "$empty" >&2
  [[ "$level" == "FAIL" ]] && status=1
fi

# Localities flagged as having no addresses must still exist in G-NAF, otherwise the name is probably misspelt.
unknown="$(q "
SELECT p.name FROM pilot_locality p
WHERE NOT p.expect_addresses
  AND NOT EXISTS (SELECT 1 FROM stg.locality l JOIN stg.state s ON s.state_pid = l.state_pid
                  WHERE s.state_abbreviation = 'NSW' AND upper(l.locality_name) = p.name) ORDER BY 1")"
if [[ -n "$unknown" ]]; then
  echo "FAIL: localities flagged expect_addresses=false are not in G-NAF (misspelt?):" >&2
  echo "$unknown" >&2
  status=1
fi

ungeocoded="$(q "
SELECT count(*) FROM stg.address_detail ad
JOIN stg.locality l ON l.locality_pid = ad.locality_pid
JOIN pilot_locality p ON p.name = upper(l.locality_name)
WHERE ad.date_retired IS NULL AND ad.alias_principal = 'P'
  AND NOT EXISTS (SELECT 1 FROM stg.address_default_geocode g
                  WHERE g.address_detail_pid = ad.address_detail_pid AND g.date_retired IS NULL)")"
echo "INFO: $ungeocoded current principal pilot addresses have no current geocode and are not loaded."

[[ $status -eq 0 ]] && echo "Completeness checks passed"
exit $status
