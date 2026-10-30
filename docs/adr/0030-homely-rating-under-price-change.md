# 0030. The Homely score sits under the price change

Status: Accepted

## Context
ADR 0028 put the "Homely rating" row right under Area and the entry form at the end of the card. You asked for both to
sit under "Price change, 12 months", the form directly beneath the score once there is one.

## Decision
On a loaded Suburb Card the order is: Area, median house price, median unit price, price change, then the "Homely rating"
row (only when a score has been entered), then the "Your Homely rating" form, then the remaining rows (sales count,
block size, flood and so on), the link to Homely and the disclaimer. Without a score, only the form sits under the price
change. Every other row keeps its place and appears once.

## Consequences
- The form splits the list of rows into two blocks; it is anchored to the price-change row by name, so renaming that row
  means changing one constant (`SuburbCardRows.PRICE_CHANGE_LABEL`).
- Supersedes where ADR 0028 placed the row and the form (the rest of ADR 0028 stands).
