# 0073. No NAPLAN year, and one Schools row on the Suburb Card that lists only the zoned schools

Status: Accepted. Supersedes the optional year of ADR 0071 and the separate "Zoned schools" row of ADR 0062.

## Context
- ADR 0071 made the NAPLAN year optional. In use the year is never wanted: the figure you copy does not come with one worth keeping, and the box
  invites invented values.
- The Suburb Card showed two school rows: "Schools" (public schools inside the suburb, ADR 0034) and "Zoned schools" (zones covering the suburb,
  ADR 0062), each with its own Details and its own list of schools. The user wants only the schools whose zone covers the suburb, so the two rows
  said overlapping, sometimes conflicting, things.

## Decision
- **NAPLAN has no year.** `NaplanEntry` is `(score)` and `NaplanScore` is `(score, updatedOn)`. The form has no year box, the client sends only the
  score, the API returns no year, the repository neither reads nor writes `school_naplan.year`, and the card reads "NAPLAN -41 vs national". The
  shared year rules (`FIRST_YEAR`, the year check, `YEAR_OUT_OF_RANGE`, the client's current-year clock) are gone.
- **The `year` column is kept and unused.** Rows may hold a year from before; dropping it would destroy them. V27 already made it nullable, so no
  migration is needed. New rows have it null; old rows keep their value, which nothing reads or overwrites.
- **A PUT body from an old client that still holds a year (or a note) is tolerated and ignored**: the JSON reader (`ApiJson`) ignores unknown keys.
  Whatever the year holds, even an impossible one, no longer causes a 400.
- **One "Schools" row on the Suburb Card** shows the zoned schools as ADR 0062 works them out (zones covering at least 25% of the suburb's lots,
  else the biggest per level; primary, then high), with the per-school lines under it (name, zone and share of lots, ICSEA, NAPLAN with Edit/Add,
  links). The separate "Zoned schools" row and its Details, and the list of schools inside the suburb, are removed from the card. With no zone
  data the row shows Unknown with the reason ("School catchments not loaded yet" or "No school catchment covers ...").
- **A saved browser layout** that names "Zoned schools" is not broken: labels the card no longer has are ignored (ADR 0039), so that entry does
  nothing. A layout that hid "Schools" now hides the zoned list; one that hid only "Zoned schools" now shows the Schools row.
- Not changed: the Lot Card's Schools row, the suburb ICSEA roll-up, both ICSEA filters, the data and the migrations. The report still carries the
  schools inside the suburb (`schools`) for the roll-up and the at-least ICSEA filter.

## Consequences
- The "ICSEA at least" filter (ADR 0035) is based on the average ICSEA of the schools inside the suburb, which the card no longer lists or
  states. A suburb can pass that filter on schools the card does not show. The zoned-ICSEA filter (ADR 0062) matches what the card shows. Whether
  to retire or relabel the inside-the-suburb filter is a later decision.
- NAPLAN figures say nothing about their age. Old rows keep a year in the database that no screen shows.
- The Suburb Card has one row fewer (17 rows), and the Schools group has a single row.
- Customise lists "Schools" once; a card's Schools row without zone data is hidden with the other Unknown rows unless shown.
