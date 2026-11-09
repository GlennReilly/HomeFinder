# 0071. The NAPLAN year is optional and the note is removed

Status: Partly superseded by 0073 (the optional year is removed altogether; the note removal stands). Supersedes the year part of ADR 0069 (and of ADR 0068, which 0069 kept).

## Context
ADR 0068 and 0069 required a year with every NAPLAN figure. The figure you copy (a difference from the national average) does not always come
with a year you can see, and being made to invent one makes the record worse, not better.

## Decision
- The year may be left out. `NaplanEntry.year` and `NaplanScore.year` are nullable; the API accepts a missing or `null` year.
- A year that is given is still checked: a whole number from 2008 (the first NAPLAN) to the current year (`NaplanRules`, sealed `NaplanCheck`).
- Migration V27 drops `NOT NULL` on `school_naplan.year` and restates its check as `year IS NULL OR year BETWEEN 2008 AND 2100`. V25 and V26 are
  not edited; rows saved with a year keep it.
- **The note is removed from the feature.** The form has no note box; the API, the shared rules (and the 200-character limit), the store and the
  cards no longer carry it. A `note` sent in a PUT body is ignored (the JSON reader ignores unknown fields). The `school_naplan.note` column is
  kept for now, not dropped, so notes typed before are not destroyed; V27 comments it as unused.
- The form's box reads "Year (optional)" and is no longer required. The card shows "NAPLAN -41 vs national" without a year and
  "NAPLAN -41 vs national (2025)" with one.

## Consequences
- The user's database needs migration V27 before a figure can be saved without a year; until then the database refuses it (503).
- A figure without a year says nothing about its age.
- Dropping the unused `note` column later needs its own migration.
- Layout (asked for in the same change): each school in the Schools rows of both cards is drawn line by line: its name with the zone, level or
  distance text; "ICSEA 1,051" (left out when not published); the NAPLAN figure with its Edit button, or the Add button alone; then its links
  (own website, now a "School website" link rather than the name, and SchoolRank). The Lot Card's Schools value names the schools without ICSEA.
