# 0075. SchoolRank address overrides, by school code

Status: Accepted

## Context
ADR 0070 builds each school's SchoolRank address from its name and the locality in its address, and never fetches or checks it. For Kotara South
Public School the built address (`.../kotara-south-public-school-kotara-south-nsw`) does not work; the working one uses the locality "Kotara"
(`.../kotara-south-public-school-kotara-nsw`), while our data says "Kotara South".

## Decision
- **Overrides only.** A small table in `SchoolRankOverrides.kt` maps a school code to the full working slug. The address builder checks it first
  and otherwise uses the generated slug unchanged. There is no general rule (for example stripping a trailing North/South/East/West), because we
  cannot fetch or check SchoolRank and have one verified example; a guessed rule could break links that work today.
- The override holds the whole slug, so any odd case can be expressed, and it applies even when the school has no locality.
- **Reporting a bad link:** tell us the school's name and the working address copied from schoolrank.com.au; we add one line to the table
  (the how-to is in the file's comment). Editing the table is the only change needed: no migration, no data load.

## Consequences
- Only listed schools change; every other address stays exactly as before.
- Each new bad link costs one line, found by a person; unreported bad links stay bad.
