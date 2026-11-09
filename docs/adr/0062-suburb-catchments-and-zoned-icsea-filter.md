# 0062. School catchments on the Suburb Card and an ICSEA filter on them

Status: Partly superseded by 0073 (the separate 'Zoned schools' row; the zones, the 25% rule and the zoned-ICSEA filter stand)

## Context
ADR 0056 loaded the NSW public school catchments (intake zones) and showed the zones a *lot* lies in. A suburb is usually split between several zones, so the Suburb
Card could not say which schools serve it, and the ICSEA filter (ADR 0035) averaged only the schools physically inside the suburb. You asked for the catchments on the
Suburb Card and an optional ICSEA filter on them.

## Decision
- **Derived at load time.** `pipeline/scripts/load-catchments.sh` also fills `suburb_catchment` (migration V24): for every suburb, each zone covering at least 5% of its lots
  and the share it covers (`lot_share` = lots whose representative point, `ST_PointOnSurface`, is inside the zone / all lots in the suburb, the point test the Lot Card uses). A
  school with several zone rows of one level (a split zone) counts each lot once. Zones are cut into small pieces (`ST_Subdivide`) so the join is quick: on the real data (171,082 lots, 2,100
  zones, a throwaway PostGIS) the whole load takes about 24 seconds. It needs the lots loaded (`load-lots.sh`); you re-run `load-catchments.sh` and nothing else.
- **Suburb Card row "Zoned schools"**, after "Schools": it names the zones ("Hamilton Public School (primary), Newcastle High School (high)") and lists each under the row with the kind
  and years of its zone, **the share of the suburb's lots it covers**, and its ICSEA (the school's name and website come from the schools data by school code; the Department's short name is
  the fallback). Zones covering **at least a quarter** of the suburb are listed, primary then high, largest first; a level where none reaches a quarter still lists its biggest zone, so a suburb
  split many ways is not left blank. The note carries the reliance warning (catchments change, a zone does not guarantee a place, check with the school), the enrolment year and the attribution
  "Based on NSW Department of Education data". Confidence is medium. A suburb with no zone covering 5%, or catchments not loaded, has the row unknown (and so left off the card, ADR 0058).
- **Filter criterion "Zoned schools' ICSEA"**: per suburb, the average ICSEA of the listed zones that have an ICSEA, **weighted by the share of the suburb each covers**, rounded; an "at least"
  slider in steps of 10 (ADR 0052). A suburb with no such school has no value and never matches; the panel says when catchments or schools are not loaded. The filters response gains
  `zonedIcsea` and `zoned` (source and the day the zones were loaded); older clients still decode it.
- Reading real data for five suburbs (throwaway database): Hamilton is 98% Hamilton Public School and wholly Newcastle High School; Wallsend is split between Wallsend (48%) and Plattsburg (40%)
  public schools; Elermore Vale between Elermore Vale (60%) and Wallsend South (39%); Charlestown between five primary zones, the biggest 40%.

## Consequences
- The share is of *lots*, not people or children, and a lot is placed by one point, so a zone boundary through a block puts the whole lot on one side. A suburb that is 48/40 between two schools
  is honestly shown as split, but the filter's average blends them.
- The zoned ICSEA pools primary and high schools, and a high school split into campuses by years (Callaghan College's 7-10 and 11-12 campuses) counts each campus, so it weighs more than one school would.
- Catchments are today's boundaries, as fresh as your last download and load. Selective, specialist, private and Catholic schools have no zones. Future zones (ADR 0056) are still ignored.
- The suburb's own "Schools" row and the existing ICSEA filter (schools physically inside the suburb) are unchanged; the two answer different questions.
