# 0083. The PDF leaves out rows with no data

Status: Accepted

## Context
ADR 0080 listed every Suburb Card row in the PDF, and a row with no value said "No data" with its reason (for example "Not computed yet"). You asked
that the words "No data" and "Not computed yet" no longer appear anywhere in the PDF. The cards already leave such rows off (ADR 0058).

## Decision
A row whose value is empty is left out of the PDF, with its note and source line, as on the card. The Schools row has no value by design and lists its
zoned schools on lines of their own, so it stays when a school is zoned and is left out when none is. A group left with no rows is left out. A suburb
with no groups at all says "Nothing to show for this suburb yet." The `NO_DATA` constant is removed. This supersedes only the "No data" part of
ADR 0080; the rest of 0080 stands.

## Consequences
- The PDF is shorter and reads the same as the cards. It no longer says which figures are missing or why.
- Tests assert that neither phrase appears in the produced text.
