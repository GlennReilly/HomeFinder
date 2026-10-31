# 0039. Card rows are hidden and re-ordered in the browser

Status: Accepted

## Context
Both cards show every factor in a fixed order. You asked to choose which rows to see and in what order (spec milestone 11). The app has
one user, so the choice does not need an account or the server.

## Decision
- Each card has a **Customise** button. While customising, every row shows up and down arrows and Hide or Show (hidden rows stay
  visible but dimmed so they can be brought back); **Done** returns to the normal view and **Reset to default** clears the choice.
- The choice is a `CardLayout` (row order plus hidden labels), kept **by row label**, one for the Suburb Card and one for the lot
  card, in the browser's `localStorage` (`homefinder.layout.suburb`, `homefinder.layout.lot`). The server and the API are unchanged.
- Rows a layout does not know (added by a later release) follow the known ones in their default order; stored labels that no longer
  exist are ignored. Renaming a row label therefore resets that row's place.
- The Homely link, the score you entered and its entry form are not rows: they stay in one block straight under the **Price change,
  12 months** row, wherever that row is moved, and keep their place when it is hidden.

## Consequences
- The choice is per browser and is lost if site data is cleared; moving it to the database is possible later and would supersede this.
- No migration and no new endpoint. The layout logic is plain Kotlin in `client` and tested on the JVM.
