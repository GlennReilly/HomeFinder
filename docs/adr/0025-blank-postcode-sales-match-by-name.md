# 0025. Blank-postcode sales match by name in the pilot councils

Status: Accepted

## Context
ADR 0018 matches a sale to a suburb by locality name plus postcode, accepted only when exactly one suburb has that pair.
The first real load (69,419 sales) left 34 sales in Newcastle and Lake Macquarie unmatched, all because the postcode
was blank (for example MOUNT HUTTON 11, JESMOND 4, GLENDALE 3). The other 35,108 unmatched sales are in the four
neighbouring councils, in suburbs outside the pilot area, and are correctly left out.

## Decision
- A sale with no postcode in a council marked `name_only_match` in `pipeline/config/sales_districts.csv` (Newcastle 008
  and Lake Macquarie 004) is matched by locality name alone, but only when exactly one loaded suburb has that name. The
  exact name-plus-postcode match still runs first and is unchanged.
- In the neighbouring councils (Maitland, Port Stephens, Cessnock, Central Coast) a blank postcode stays unmatched, because
  there the locality may be a same-named suburb outside the pilot area.
- A sale that has a postcode which does not match is never rescued by name.
- Tests cover a pilot council matching by name, a neighbouring council staying unmatched and a name shared by two
  suburbs staying unmatched.

## Consequences
- About 34 more sales count (about 0.1 %). Medians barely move; the point is that no pilot-council sale is lost for a
  reason that has nothing to do with the sale.
- `sales_districts.csv` gains a column, so a council added later must say whether it may match by name.
