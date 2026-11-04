# 0047. Weighted-score mode for suburbs

Status: Accepted

## Context
ADR 0035 planned a second filter mode beside "all must pass": a weighted score, so that a suburb strong on one thing can make up for a weak one.
ADR 0046 gave the panel four criteria (ICSEA, beach distance, park share, homes rented).

## Decision
- The panel gets a **Score suburbs** section. Each criterion that has data gets a **weight** slider (0 to 5; 0 ignores it) and a choice of which end is
  better ("higher is better" or "lower is better"). Defaults: higher ICSEA and park share, lower beach distance and rented share.
- A criterion is scaled across the suburbs that have a value for it (lowest 0, highest 1, flipped when lower is better). A suburb's **score** (0 to 100) is the
  weighted average of the criteria it has a value for. Everyone equal on a criterion scores 0.5 on it.
- **A missing value does not exclude a suburb** here (unlike the pass/fail ranges of ADR 0035): the suburb is scored on what it has and marked **partial**. A suburb
  with no value for any weighted criterion is not scored. A criterion whose data is not loaded is ignored.
- On the map, scored suburbs are shaded blue, deeper for a higher score, in a layer under the pass/fail wash, so the ranges above still apply on top. The panel lists the
  top 10 ("1. Hamilton, 82 (partial)").
- Computed in the browser from the one filters response, so moving a slider is instant. `SuburbFilterValue` gains `name`, so the ranking can name suburbs.

## Consequences
- Scores are relative to the 173 suburbs, not absolute: a score of 100 means best of the group on the weighted mix.
- Scaling by the lowest and highest value means one outlier compresses the rest; a rank-based scale is a possible later change.
- A partial score is not comparable with a full one in a strict sense; it is marked, not hidden.
- Transport will join as one more criterion once its data exists.
