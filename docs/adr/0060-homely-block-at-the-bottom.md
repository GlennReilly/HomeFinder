# 0060. The Homely block is the last section of the Suburb Card

Status: Accepted

## Context
The Homely block (the score you typed in, the link to the suburb's reviews on Homely and the form to edit the score) sat straight under the "Price change, 12 months" row,
wherever that row was moved to (ADR 0030, 0043; ADR 0039 says it stays under the price change). The card got longer (SEIFA, crime, hidden-row note), so the block ended up in
the middle of the data, and the code split the rows in two lists around the price-change row, which also had to cope with that row being hidden or unknown (ADR 0058).

## Decision
- The Homely block is the **last section of the card body**: after every data row, after the school list under the Schools row and after the "N rows without data are hidden"
  note, and before the disclaimer line, which stays the very last line.
- It is not a row: Customise (ADR 0039) lists only data rows, so the block cannot be hidden or moved, and it looks the same while customising and when hidden rows are shown.
- The card draws one list of rows; the split around the price-change row (`UnknownRows.splitAfter`) is removed. The block's own parts and the "Edit the Homely rating" form
  (ADR 0043) are unchanged.

## Consequences
- The data rows read straight through without an interruption, and the Homely block no longer depends on where one row sits or whether it is drawn.
- You scroll to the bottom to reach the Homely link and the form; the sticky header (ADR 0053) keeps the suburb's name in view while you do.
- Saved layouts need no change: the block was never part of them.
