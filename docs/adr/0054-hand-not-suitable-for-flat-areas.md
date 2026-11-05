# 0054. Height above drainage is not offered in flat suburbs

Status: Accepted

## Context
ADR 0040 added a Lot Card row, "Height above drainage" (HAND), checked against the council's flood map in one hilly suburb (Elermore Vale, AUC 0.93). Flat harbour suburbs were left
inconclusive because the map could not be placed. A later check (2026-11-05) of 9,374 lots in 12 flat suburbs against the council's 10% annual-chance extents (Map 1-A, placed by the
scale bar and checked by eye to about 10 m) found the hint does not work there:
- AUC 0.50 for HAND and 0.51 for height above sea level (chance); 0.93 and 0.84 in Elermore Vale. The result did not move across 25 shifts of up to 38 m.
- Share of lots in a flood area by HAND band: 22% under 1 m, 21% from 1 to 2 m, 32% from 2 to 5 m, 40% from 5 to 10 m, 29% over 10 m: it does not fall as the lots rise.
- 72% of the lots read under 1 m (Carrington 90%, Hamilton 91%, Wickham 87%, Hamilton North 96%), so differences between lots are smaller than the elevation data's noise,
  and the council's flat-land flooding is overland flow, ponding and tidal water, which HAND does not measure.

## Decision
- Where **more than half** (`FLAT_SUBURB_SHARE_ABOVE` = 0.5) of a suburb's lots that have a HAND result read **under 1 m**, the Lot Card's Height above drainage row reads
  **"Not suitable for flat areas"**, with no confidence label, and its note gives the suburb's percentage and the reason. A suburb with fewer than 20 lots with a result
  (`FLAT_SUBURB_MIN_LOTS`) is not judged and shows the hint as before.
- The server works the share out when it reads a lot's drainage (one query over the suburb's lots, about 50 ms for the largest suburb, using the existing `lot.suburb_id` index), so
  nothing needs re-loading. `DrainageProximity` gains `FLAT_AREA`; the figures stay in the report but the card does not show them.
- Elsewhere the row is unchanged (low confidence, "not a flood result").

## Consequences
- On the current data 25 suburbs are flagged, among them Hamilton, Hamilton North, Hamilton South, Carrington, Wickham, Maryville, Islington, Broadmeadow, Swansea, Williamtown and
  Fullerton Cove.
- A hilly lot inside a flat suburb loses its hint too; a suburb-level rule is deliberately blunt.
- A proper flat-land check needs a georeferenced flood layer from the council. If one arrives, the rule can be revisited.
- Partly supersedes ADR 0040 (its hint is no longer offered in flat suburbs).
