# 0072. One closed Details section per row holds the note and the source

Status: Accepted. Supersedes ADR 0063.

## Context
ADR 0063 put each row's source in a closed "Source" section, and rows with an explanatory note had a second closed "Details" section above it.
Two links per row, stacked with tuned overlaps (PRs 85 to 90), made the cards busier than the information warranted. You asked for one link.

## Decision
- Every card row (Suburb Card and Lot Card) and the filter panel has at most one closed `details` headed "Details" (`DetailsFold` in
  `HtmlNames.kt`). Inside: the row's note first, then its source line starting "Source: " (source, as-of date and confidence), with a little
  space between them. The filter panel's lines read "Source for <criterion>: ...".
- A row with only a note or only a source still gets the section; a row with neither gets none. It is closed by default.
- The 44 px tap area of the summary and the gap above it (`--details-gap`) stay. The CSS that stacked Details above Source
  (`--details-stack-gap`, `--summary-slack`, the `:has()` and `.note + .provenance` rules) is removed.
- The "N rows without data are hidden. Show them" note is not a Details section and is unchanged.
- The "Look these schools up on My School" line under the Schools row is removed; the NAPLAN form keeps its own My School link.

## Consequences
- One tap shows everything known about a row's value; nothing that was shown before is lost.
- Tests and styles refer to `details-fold`, `details-note` and `details-source`; the old `note-details` and `source-details` classes are gone.
