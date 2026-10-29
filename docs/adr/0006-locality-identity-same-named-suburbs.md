# 0006. Identify the right G-NAF locality when suburb names repeat

Status: Accepted

## Context
ADRs 0003 and 0004 matched G-NAF addresses to the pilot region by locality name within NSW. The first real PR 4 link
run showed that 2,207 unmatched addresses belonged to Sydney's Silverwater, 70 km from the pilot area, and that
Balmoral, Maryland, Carrington, Mayfield and Hillsborough had the same problem. Name alone does not identify a suburb.

## Decision
- Supersedes the name-only match in ADR 0003 and 0004 for the address filter.
- A G-NAF locality with a pilot name is accepted only if at least 50% (`LOCALITY_MIN_SUBURB_SHARE`) of its current
  principal geocodes lie inside the official suburb of the same name. The decision is per locality, not per address,
  so kerb-side points near a boundary do not drop real addresses.
- Every decision is stored in `locality_decision` (addresses, share inside, accepted) and rejected localities are
  printed by `check-gnaf.sh`.
- Load order is now: `load-lga.sh`, `load-suburbs.sh`, `load-gnaf.sh`, `load-lots.sh`, `link-address-lots.sh`.
  `load-gnaf.sh` refuses to run without suburbs.
- The completeness check against the LGA polygons is unchanged and still proves nothing inside the councils is missed.

## Consequences
- Rebuilding addresses clears `address_lot`; rerun `link-address-lots.sh` afterwards (lots are kept).
- A genuine pilot locality whose geocodes mostly sit outside its boundary would be rejected, and the existing "pilot
  locality with no loaded address" check would fail loudly, so it cannot be dropped silently.
- The 50% threshold is a judgement; the stored shares make it easy to review.
