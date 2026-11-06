# 0066. Filters are grouped into folding sections

Status: Accepted

## Context
The Filters tab lists eight criteria one after another, with a slider and two hint lines each, so the tab is long and the criterion you want is often off screen.
The card rows already fold into groups (ADR 0064).

## Decision
Criteria sit in named groups, each a native `details` with the same look as the card groups (`client` `FilterGroups`): People and area (socio-economic advantage,
homes rented), Crime (violent, break-ins/car/home theft), Location (beach distance, parks), Schools (ICSEA, zoned schools' ICSEA). A criterion no group lists lands in a
final "More" group. The first group starts open, as does any group holding a switched-on criterion, so an active filter is never hidden; the others start closed.
The group title carries "N on" (empty when none), updated as checkboxes and sliders change. Open and closed state lives in the page (the panel is drawn once) and is
not saved, as for the card groups. Criterion ids (`filter-<key>-on`, `-limit`, `-limit-value`), Reset all, the summary line, the live count on the tab and the Source lines are unchanged.

## Consequences
- The tab is shorter; the cost is one click to open a closed group. Browser tests must open a group before using its criteria.
- A new criterion should be added to a group, or it shows under "More".
- Reset all leaves groups as they are (open groups stay open).
