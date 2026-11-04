# 0041. Cards show only rows with a reliable source

Status: Accepted

## Context
Both cards listed every factor from the spec, each as "Unknown" until its pipeline landed. An audit of the cards against the real data
(4 November 2026) found rows that can never be filled from anything we may use, and a few that showed misleading values:
- **Flood** (both cards): no free statewide layer covers Newcastle, Lake Macquarie or Maitland, and the council study found is PDF maps only
  (ADR 0036, 0040). The Lot Card's Height above drainage is the indicative stand-in and says it is not a flood result.
- **Wall material** (Lot Card) and **Likely brick** (Suburb Card): the only source is listing text, and portals forbid robots (ADR 0027).
- **Estimated price** (Lot Card): no model and no comparable-sales method is in place.
- **Parks** read "0% of the suburb is park" for 58 of the 173 pilot suburbs, where OpenStreetMap simply has no park of at least 0.5 ha mapped.

## Decision
- The cards no longer show Flood (both), Wall material, Likely brick or Estimated price. The fields stay in `LotReport` and `SuburbReport` (still
  explicit unknowns) so a source can be wired in later without a model change; only the card rows are dropped. Saved layouts keep working: a
  layout ignores labels that no longer exist (ADR 0039).
- A suburb with no mapped park shows Parks as Unknown with the note "No park of at least 0.5 ha is mapped ... which may be incomplete",
  the same way a missing beach is reported.
- Rows with a plausible planned source stay: Backyard aspect (needs each lot's street side), Transport (needs the user's Transport for NSW key)
  and the Lot Card's Schools (the suburb's school data could fill it).
- Thin sales were already flagged: a median needs at least 5 sales, and a row under 15 sales shows "low confidence" with its sales count, so no change.

## Consequences
- The suburb card has 10 rows and the lot card 10. A row comes back by adding it to the row list when its source exists.
- Flood stays unanswerable on the cards until a licensed, georeferenced layer is found; Height above drainage is the only hint.
- Known and still open: 10,649 lots show a height of 0.0 m and slope 0% because empty DEM tiles are read as zero (ADR 0040); a separate change.
