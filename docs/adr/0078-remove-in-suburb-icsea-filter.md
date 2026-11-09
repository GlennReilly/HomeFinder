# 0078. Remove the in-suburb ICSEA filter; NAPLAN first in Schools

Status: Accepted

## Context
ADR 0035 added a suburb filter on the average ICSEA of the public schools inside the suburb (ADR 0034). Since ADR 0073 the Suburb Card lists only the zoned schools
(ADR 0062), so the filter was measuring schools the card no longer shows, and its label ("School ICSEA (average of public schools in the suburb)") sat beside the
zoned-schools ICSEA filter and confused the two.

## Decision
- The `ICSEA` filter criterion is removed from the Filters tab (its `filter-icsea-*` element ids go with it).
- The Schools group lists the NAPLAN filter first, then Zoned schools' ICSEA.
- The panel's "nothing loaded" text for schools now comes from the zoned-schools criterion.

## Consequences
- The `/v1/suburbs/filters` fields `averageIcsea`, `icseaSource` and `icseaAsOf` stay in the API and the shared model, unused by the page; no server or database change.
- A user's saved filter limits for the old criterion are simply ignored.
- ADR 0035 stays as written and is marked partly superseded in the index.
