# 0052. One slider with a fixed direction for each filter

Status: Accepted (supersedes the two-slider parts of 0035 and 0046)

## Context
Each filter criterion had two sliders, "At least" and "At most" (ADR 0035, 0046). In use, only one end ever mattered: a good school average, a short distance to the
beach, a low crime rate. The second slider was clutter, and with seven criteria the panel had fourteen sliders.

## Decision
- Each criterion has **one slider** (one threshold) with a **fixed direction** that suits it: **at least** for school ICSEA, park share and SEIFA decile; **at most** for
  beach distance, homes rented and both crime rates. The label reads "At least 1050", "At most 5 km", "At most 40 per 1,000" (`Direction`, `FilterCriterion.direction`).
- The slider spans the criterion's bounds as before and **starts at the end every suburb with a value passes** (the lowest value for "at least", the highest for "at most"),
  so switching a criterion on changes nothing until the slider moves. The limit itself passes.
- Unchanged: the switch (moving the slider still switches its criterion on), all switched-on criteria must pass, a suburb with no value never matches and is counted beside
  the slider, sources, not-loaded messages, the live count on the Filters tab and the zero-match hint (ADR 0048).
- Element ids become `filter-<key>-on`, `-limit` and `-limit-value` (the `-min` and `-max` ids are gone). `FilterSelection` holds one limit per criterion.
- The crime criteria's unit is " per 1,000" so their label reads "At most 40 per 1,000".

## Consequences
- A "between" filter (for example a middling ICSEA) is no longer possible; the direction is fixed per criterion. A toggle per criterion or a two-thumb slider can be added
  later if that is missed.
- The panel is half as tall for the same criteria, and the crossing logic that kept two sliders from passing each other is gone.
