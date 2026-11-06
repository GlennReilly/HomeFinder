# 0065. The suburb filter shading fades out at lot zoom

Status: Accepted (amends 0035 and 0046 on how the filter shading is drawn)

## Context
The filter panel washes out suburbs that fail and tints those that pass. At zoom 16 and above the map shows lot outlines and a tap selects a lot, so a suburb-sized
green tint says nothing about the lot in view and covered the whole screen.

## Decision
- The two filter layers (`suburb-filter-match`, `suburb-filter-dim`) have a `fill-opacity` that is full to zoom 15 and falls to 0 at zoom 16, the lot-outline zoom
  (`TileContract.LOTS_MIN_ZOOM`).
- The selected-suburb tint (0057) is unchanged. Filter results, counts and the Filters tab are unchanged.
- Looking into a report that the lot card was replaced by Filters: clearing the selection opens Filters (0048), and a tap on a point with no lot clears it. No filter
  change touches the selection, so that is intended and unchanged.

## Consequences
- Lots are not tinted by suburb-level filters; the shading returns when zooming out.
