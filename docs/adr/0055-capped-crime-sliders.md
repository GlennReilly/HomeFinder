# 0055. Capped crime sliders with a "No limit" position

Status: Accepted

## Context
The two crime criteria (ADR 0051) are "at most" sliders that start at the right end, where every suburb with a rate passes (ADR 0052). Rates have a long tail: on the real data
(132 suburbs with a rate) violent crime runs from 4.6 to 227 per 1,000 residents with a median of 21 and a 90th percentile of 54; break-ins and theft run from 8.6 to 338 with a median of
33 and a 90th percentile of 96. A slider spanning lowest to highest left most suburbs in its left quarter, so a small movement changed the result a lot.

## Decision
- A criterion can have a **cap percentile** (`FilterCriterion.capPercentile`, for "at most" criteria only). Both crime criteria use **90%** (`CRIME_CAP_PERCENTILE`).
- The cap is the value at that rank (nearest rank), rounded up to a step. The slider spans the lowest value to the cap **plus one step**: the last position means **"No limit"**,
  shown as "No limit" with no words before it, and applies no upper bound. It is where the slider starts, so it still matches every suburb with a value (the outliers included).
  On the real data: violent crime 4 to 54 and No limit; break-ins 8 to 96 and No limit.
- A suburb with no value never matches, even at "No limit" (ADR 0035). Reset all puts the slider back on "No limit". Moving it off "No limit" switches the criterion on, as before.
- When no suburb lies above the cap (a small data set, where the percentile is the highest value), there is no extra position and the slider spans the data as before.
- Other criteria are not capped.

## Consequences
- The 10% of suburbs above the cap can only be separated from the rest by "No limit" or not at all; a limit above the cap is not offered. That suits the use (keep suburbs under a sensible rate).
- The cap moves with the data, so a reload of the crime data can change the slider's range.
- The browser tests use three suburbs, too few for a cap, so the capped slider is covered by unit tests.
- Refines ADR 0052.
