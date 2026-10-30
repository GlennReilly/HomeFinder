# 0033. Suburb roll-up of the OpenStreetMap features

Status: Accepted

## Context
The Suburb Card's beach, parks and shopping rows were still "Not computed yet", although the lot card already shows the same
OpenStreetMap data per lot (ADR 0023, 0024). The spec's Suburb Card defines them: distance to the beach from the suburb centre
and from its nearest edge, park area as a share of the suburb, and shopping centres and supermarkets inside or within 2 km.

## Decision
- A new table `suburb_amenity` (migration V14) is rebuilt by `refresh_suburb_amenity()` at the end of `load-osm.sh`, from the
  `osm_feature` table of ADR 0023. All distances are straight lines in metres (MGA zone 56), as for lots.
- **Beach:** the ocean beach nearest the suburb's edge (the same ocean-beach rule as lots: on the mapped coastline); the distance
  from there to the suburb's boundary ("edge", 0 when the suburb touches it) and to the point its name is drawn at ("centre").
- **Parks:** the union of parks of at least 0.5 ha, clipped to the suburb, so overlapping parks are not counted twice, divided
  by the suburb's area. The server clamps the share to 0 to 1.
- **Shopping:** the number of shopping centres (`shop=mall`) and supermarkets whose position is in the suburb or within
  `SUBURB_SHOPPING_RADIUS_M` (default 2,000 m, the spec) of its boundary.
- **Report:** each is an `Attribute` with source "OpenStreetMap contributors (ODbL)", the age of the OSM data as its as-of date and
  MEDIUM confidence with a note. No shops at all is shown as 0 and 0 with LOW confidence and says none are mapped, because an
  unmapped shop is more likely than no shop. Before the OSM load, or without a beach in the data, the row is unknown with a reason
  ("OpenStreetMap data not loaded yet", "No ocean beach found in the OpenStreetMap data loaded").
- The card's wording for these rows already existed, so the web app is unchanged.

## Consequences
- Measured on your 173 suburbs with the loaded OSM data: the roll-up takes about 2.6 seconds.
- Reloading the suburbs (`load-suburbs.sh`) clears `suburb_amenity` with them; run `load-osm.sh` again to rebuild it.
- Straight-line distances ignore water: a suburb across Lake Macquarie from a beach counts the distance over the lake.
