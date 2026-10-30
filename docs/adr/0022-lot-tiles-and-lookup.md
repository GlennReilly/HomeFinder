# 0022. Lot tiles, lot lookup and the basic lot card

Status: Accepted

## Context
The spec's zoom table says lot boundaries are drawn from zoom 16 and a tap there selects a lot. You chose a basic lot
card for now (the lot, its addresses, its measured block size and its suburb, with every other factor an explicit
unknown) and outlines from zoom 16. PR 12 is split: 12a is the server and shared contract (this decision), 12b is the
client and web UI.

## Decision
- **Tiles.** `GET /tiles/lots/{z}/{x}/{y}.pbf` serves lot boundaries for zoom 16 to 18 (a tile outside that range is `204`;
  clients over-zoom beyond 18, as for suburbs). Tiles are read straight from the `lot` table with `ST_AsMVT` and the
  existing GiST index: at street zoom a tile holds a few hundred lots, so nothing is precomputed or simplified, unlike
  suburb tiles. The layer is `lot` with one property, `lot_id`. The tile route now has a small layer registry, so a
  layer is just its zoom range and its source; suburbs (5 to 14) and lots (16 to 18) hand over with no overlap.
- **Lookup.** `GET /v1/lots/at?lat=&lon=` returns a `LotRef` (lot id, suburb id) for the smallest lot whose boundary
  covers the point (`ST_Covers`, so boundaries count), `404` when none. `GET /v1/lots/{lotId}` returns a `LotReport`.
  Both need the API key (header only) and the database, with the same 400/404/503 rules as the suburb endpoints.
- **The basic lot card** (`LotReport`): lot id, the cadastre's parcel label (for example `1//DP100`), suburb and
  postcode, up to 50 linked addresses formatted the Australian way and sorted by street and number, and the lot's area
  (cadastre, calculated in GDA2020 / MGA 56, high confidence). Every other property factor is an explicit unknown with
  the source that will supply it. It is not a `PropertyReport`, which is keyed by an address.
- **Strata.** A lot on a strata or community plan (parcel key with SP or CP) reports its area at medium confidence and
  says the area is the whole parcel's, not one unit's share, since a unit's own area is not in the cadastre.
- **Addresses linked by `nearest_lot`** (ADR 0007, low confidence) are listed like the others; the card does not yet
  show the link method.

## Consequences
- A lot tile costs one indexed query and a transform of a few hundred geometries; measured on the real data in the PR.
- Lot tiles are not cached by the server beyond the `private, max-age=300` header the tile route already sets.
- Building footprints, the full Property Card and aspect, slope and flood all build on this lot id later.
