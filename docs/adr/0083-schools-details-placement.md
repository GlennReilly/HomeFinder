# 0083. The Schools row's Details fold follows the list of schools

Status: Accepted

## Context

ADR 0072 puts a closed "Details" fold (note and source) in every card row. The Schools row draws no label or value, because the schools are listed one by
one below it, so its fold sat alone under the group heading with a blank gap above it and above the first school: a bare link that belonged to nothing.

## Decision

A row with no value line draws its Details fold after the content it describes (the list of schools) instead of before it. Other rows are unchanged. The
fold keeps the source ("Source: ...") and the note ("Your own figure, typed in." for NAPLAN), so nothing is lost. An e2e test checks the order.

## Consequences

- No empty label cell or gap under the "Schools" heading; the list starts straight away.
- The Lot Card draws its rows through its own function and always shows the label and value, so it is not affected.
