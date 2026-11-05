# 0063. Source lines are collapsed by default

Status: Accepted

## Context
Every card row carried a "Source: … · as of … · confidence" line, and the filter panel listed one source line per criterion. With 16 rows on the Suburb Card these lines
made the card long and noisy, though the source is only wanted now and then.

## Decision
Each row's provenance sits in a closed `<details class="source-details">` whose summary reads "Source"; opening it shows the source, as-of date and confidence. The filter
panel groups its source lines in one closed "Source" section. Nothing is dropped: ADR 0019's rule that every row names its source still holds, one click away.

## Consequences
- Cards are shorter; the source is a click away and works by keyboard (native `details`).
- The "Source:" prefix is gone from the text (the summary says it), so tests and e2e checks match the bare source name.
- Open/closed state is the browser's own and resets when the card redraws.
