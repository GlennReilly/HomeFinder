# 0069. The NAPLAN figure is a signed difference from the national average

Status: Accepted. Supersedes the score range and the Source wording of ADR 0068.

## Context
ADR 0068 stored one NAPLAN figure per school as an absolute national scale score from 0 to 1000. The figure you actually want to
type is how far a school sits above or below the national average, as shown on cards such as SchoolRank's "NAPLAN -41 vs
national". That figure can be negative, so the 0 to 1000 check refused it. You are likely to read it from SchoolRank, whose
terms we have not read, so the app should not say the figure came from My School or ACARA.

## Decision
- **Meaning.** The one number per school is the difference from the national average in whole points, above (+) or below (-),
  for example -41 or +25. The year and the optional note stay. The field keeps its name (`score`) in the API and the table.
- **Range.** `NaplanRules.MIN_SCORE = -1000`, `MAX_SCORE = 1000`: a loose sanity bound, not a statement about NAPLAN. Anything
  outside it, or not a whole number, is refused with the existing sealed `NaplanCheck.Invalid` / `400 invalid_naplan`.
- **Migration V26** replaces the `school_naplan.score` check with `BETWEEN -1000 AND 1000` and adds a column comment. V25 is not
  edited. Every row saved under V25 (0 to 1000) stays valid.
- **Display.** The cards show the sign: "NAPLAN -41 vs national (2025)", "NAPLAN +25 vs national (2025)", "NAPLAN 0 vs national
  (2025)". The form's label reads "Difference from national average (points, may be negative, e.g. -41)".
- **Input box.** A plain text box with no numeric `inputmode`, `autocomplete` off and placeholder "-41". Phone number pads often
  have no minus key, and a `type=number` box throws away "+25" or a typographic minus, which other sites print. The form accepts
  an optional "+" or "-" (and reads the typographic minus and dashes as "-"), then applies the shared rules.
- **Wording.** A row showing a figure adds the neutral Source text "Your own figure, typed in. NAPLAN results are published by
  ACARA on My School." The form keeps a plain link to https://www.myschool.edu.au/. The "Source: Australian Curriculum,
  Assessment and Reporting Authority (ACARA)" attribution line is dropped, because it implied the figure was copied from My School.
  There is no link to SchoolRank.

## Consequences
- The user's database needs migration V26 before a negative figure can be saved. Until then the database refuses it (503).
- A figure saved before this change as a scale score (for example 512) now reads as "+512 vs national". Re-enter it as a difference.
- The figure's provenance is only what you type in the note; the app makes no claim about where it came from.
