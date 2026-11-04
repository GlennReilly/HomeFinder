# 0045. The Lot Card shows the nearest public primary and high school

Status: Accepted

## Context
The Lot Card's Schools row was always Unknown, while the Suburb Card lists the public schools inside the suburb (ADR 0034). For a lot, what matters
is how far the nearest schools are, not which schools happen to lie inside the suburb's boundary: a house on the edge of a suburb can be next to a school
in the next one. School catchments (the zones that decide where a child may enrol) are not in the data we have.

## Decision
- The row shows the **nearest public primary school and the nearest public high school** to the lot, nearest first, each with its kind, the straight-line
  distance to the lot's nearest edge, an estimated walk (straight line x 1.3 at 80 m a minute, as for beaches and parks, ADR 0023) and its ICSEA where ACARA
  publishes one: "Hamilton Public School (Primary School), 600 m away, about 10 min walk (estimated), ICSEA 1,051".
- The server reads the 30 schools nearest the lot from the `school` table (spatial index, then true metre distance) and the factory picks the nearest of each
  kind by the data's own kind label (Primary or Infants school, Secondary or High school). Where there is neither kind, it shows the nearest two of any kind.
- The attribute is MEDIUM confidence with the Department of Education's extraction date. Its note says: straight line, not catchments or routes; private and
  Catholic schools are not in the data; ICSEA measures advantage, not quality.
- Websites are kept only when they are http or https addresses, as on the Suburb Card.

## Consequences
- No pipeline change and no new table: it uses the schools already loaded for the Suburb Card (`load-schools.sh`). Before that load the row says "School data not loaded yet".
- The nearest school is not necessarily the one a child would be allowed to attend: catchments are a separate, still missing, dataset.
- Central, community, special and combined schools count as neither primary nor high, so they appear only when the data has no primary or high school near the lot.
