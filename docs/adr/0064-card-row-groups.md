# 0064. Card rows are grouped into folding sections

Status: Accepted

## Context
The Suburb Card has 16 rows and the Lot Card around 10. Prices, people, crime, location and schools ran together in one long list, so the row you wanted was
often off screen. ADR 0039 let you hide and re-order rows but only as one flat list.

## Decision
Rows sit in named groups, each a native `details` with a heading (`client` `CardGroups`). Suburb Card: Prices and blocks, People and area, Crime, Location,
Schools. Lot Card: The block, Nearby. A row no group lists (one added by a later release) lands in a final "More" group, and a group with nothing to draw is left out.
The first group starts open, the others closed. A group you fold open or shut stays so for the page load, across suburbs and redraws, but is not saved.

Customising opens every group, and each group heading gets up/down buttons; row buttons move a row within its group only, never across groups. The group order is saved
with the row order and hidden rows in the browser layout, as `&Title` lines beside the existing `+` and `!` lines, so layouts saved before groups still read. Hiding unknown rows (ADR 0058)
works per group: unknown rows are counted across the card for the "N rows without data are hidden" note, and a group whose rows are all hidden or unknown is not drawn. The for-sale links of ADR 0061 stay with their row's group.
The Homely block (ADR 0060) stays the last section, outside the groups.

## Consequences
- The card is shorter and scans by topic; the cost is one click to open a closed group.
- Row-level moves are narrower than in ADR 0039 (no crossing groups); groups move as a whole.
- The group lists are code: a new row should be added to a group, or it shows under "More".
- Browser tests that look at rows outside the first group must open that group first.
