# 0058. Rows without data are left off the cards

Status: Accepted

## Context
Every card row used to appear even when its value was unknown, in italics as "Unknown" with its source and reason (ADR 0019). As data sources were added, many suburbs
and lots showed a long run of "Unknown" rows (Transport is always unknown today; thin sales leave the price rows empty), which buried the facts that are known.

## Decision
- A row whose value is unknown is **left off** the Suburb Card and the Lot Card. A row with a value is never left off, whatever its confidence.
- Honesty is kept by a small muted line under the rows when any are left off: "3 rows without data are hidden." with a **Show them** button. Shown, the rows appear
  as before, with "Unknown", their source and the reason, and the line becomes "Showing 3 rows without data." with **Hide them again**.
- That choice is **per page load** (not saved in the browser): each load starts with the unknown rows left off. This is simpler than a stored preference and the
  default is what the user asked for.
- **Customise** still lists every row (unknown ones too) so rows can be hidden and reordered (ADR 0039); no note is drawn while customising. Rows you hid yourself
  are not counted as "without data".
- The Homely block still sits under the "Price change, 12 months" row's place even when that row is unknown or hidden. (The place is now taken from the full row
  order, which also fixes the block jumping to the top when the price change row was hidden by the user.)
- The logic lives in `UnknownRows` (client module, unit tested); the page only keeps the shown/hidden flag and wires two buttons.

## Consequences
- This changes the "never blank, always Unknown" presentation rule of ADR 0019 for the default view; the report model still carries every field with its source and note.
- A card with little data can be short. The note says how many rows are left off, so missing data is visible without being a wall of "Unknown".
- Filters and the API are unchanged: a suburb with no value is still unknown there (ADR 0035, 0046).
