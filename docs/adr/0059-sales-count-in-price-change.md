# 0059. The sales count sits in the price change row

Status: Accepted

## Context
The Suburb Card had a row of its own, "Sales behind the prices", showing one number (the house and unit sales in the last 12 months). On its own it said little, and it is
exactly the figure that tells you how far to trust the median prices and the price change above it.

## Decision
- The row is removed. Its number moves into **"Price change, 12 months"**, in brackets after the value: "+4.2% (31 sales)" ("1 sale" for one).
- The row's note reads the price change's own reasoning first ("House median price, from 40 and 38 house sales ...") and then the house/unit split of the sales and the
  warning that sales reach the Valuer General weeks after contracts. The row keeps the price change's own source, as-of date and confidence.
- While the price change is unknown (too few house sales to compare two 12-month periods) the value stays empty and the note is that reason, which already carries the counts.
  Since ADR 0058 leaves unknown rows off the card, the count is then visible only after "Show them".
- `SuburbReport.salesCount` stays in the API and the model unchanged; only the card row goes.
- A layout saved in a browser (ADR 0039) that lists "Sales behind the prices" in its order or hidden rows keeps working: the layout code ignores labels that no longer exist in
  the order, and a stale hidden label changes nothing (a test covers it). Such a layout still counts as customised, so "Reset to default" stays offered until pressed.

## Consequences
- One row fewer (17 rows on a card with everything loaded) and the evidence for the price change sits beside it.
- A suburb with a median price but too few sales for a price change shows no sales count by default.
