# 0004. Include every suburb that overlaps the council areas, and prove completeness

Status: Accepted

## Context
ADR 0003 selected pilot suburbs by centroid inside the LGA polygons. Comparing with suburbs that merely overlap the
LGAs showed 28 suburbs left out (for example Thornton, Tomago, Williamtown, Gwandalan, Summerland Point), so
addresses inside the councils would be missing. The requirement is no missing addresses.

## Decision
- Supersedes the region filter in ADR 0003: a suburb is in the pilot if it overlaps either LGA at all. The list
  grows from 144 to 172 suburbs, and border suburbs are loaded whole so suburb statistics stay correct.
- LGA polygons (GDA2020, from the NSW Spatial Services LocalGovernmentArea layer) are loaded into `lga` by
  `scripts/load-lga.sh`.
- `scripts/check-gnaf.sh` runs at the end of every G-NAF load and fails when
  1. a current principal address inside an LGA polygon has a locality that is not in the pilot list;
  2. a pilot locality has no loaded address, which catches name mismatches (`CHECK_EMPTY_LOCALITIES=warn` downgrades it);
  and reports the count of current addresses skipped for having no current geocode.

## First run on the real G-NAF (Aug 2026)
- 252,825 addresses loaded. The check found `LAKE MACQUARIE`, a G-NAF locality with 4 street addresses that I had
  wrongly excluded as a water body; it is now in the list (173 localities).
- Frazer Park, Olney and Point Wolstoncroft are valid G-NAF localities with no current addresses. They are flagged
  `expect_addresses=false` (migration V5); the check still requires them to exist in G-NAF so a misspelling fails.

## Consequences
- Some addresses outside the councils are loaded, in the overlapping border suburbs.
- Completeness is proven against the real G-NAF on every load, not assumed. The check is covered by the fixture test,
  including a deliberate gap.
- Addresses with no geocode cannot be placed on the map, so they are reported, not loaded.
- Boundary versions can drift; rerun the check after each G-NAF release.
