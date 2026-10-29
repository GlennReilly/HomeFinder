# 0007. Nearest-lot link as a low-confidence fallback

Status: Accepted

## Context
After ADR 0006 the first real link run matched 97.7% of 250,105 addresses (196,957 by legal parcel id, 47,392 by
point-in-polygon). Of the 5,756 left, 5,680 had no parcel id and a geocode in the road or kerb: 5,621 were within 30 m
of a lot (4,620 at 5 to 15 m) and only 62 were further away. The rest were 67 unknown parcel ids and 9 strata.

## Decision
- Extends ADR 0005: addresses still unmatched link to the nearest lot within 30 m (`NEAREST_LOT_MAX_M`), with method
  `nearest_lot` and the distance stored in `address_lot.distance_m` (migration V8).
- The nearest 5 lots by degrees are re-ranked by true distance in metres (GDA2020 / MGA zone 56), ties by lot id.
- The link is low confidence. Later cards must show it as such, per the provenance rule in the spec.
- Addresses beyond 30 m stay unmatched and are counted by `link-address-lots.sh`.

## Consequences
- At 5 to 15 m the nearest lot can be the neighbour, so these links can be wrong; the stored distance allows review.
- The 30 m limit is a judgement made from the measured distribution.
