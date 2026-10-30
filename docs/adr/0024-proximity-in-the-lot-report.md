# 0024. Proximity in the lot report

Status: Accepted

## Context
ADR 0023 stores, per lot, the nearest beach, parks and shops and the park area nearby. The lot card must show them with
the provenance the spec demands, and be honest that distances are straight lines and OpenStreetMap is incomplete.

## Decision
- `GET /v1/lots/{lotId}` reads `lot_nearest`, `lot_amenity` and `osm_import` for the lot. A missing table or an empty
  import means "no proximity data", not an error; any other database failure is still a 503.
- **Beach** is the nearest beach on the mapped coastline; **parks** are the three nearest parks of at least 0.5 ha plus
  the park area within 800 m; **shopping** is the nearest supermarket and the nearest shopping centre. Each carries
  the source "OpenStreetMap contributors (ODbL)", the age of the OSM data as its as-of date (a UTC date, so it does not
  depend on the server's time zone) and MEDIUM confidence, because OSM coverage is uneven and the beach rule is a heuristic.
- **Walking time** is an estimate made on the server: straight line times 1.3 at 80 metres a minute, rounded to a whole
  minute, and the note says so. It is never a route. The card shows it only up to 60 minutes; beyond that it says only
  the distance, since "171 min walk" tells you nothing.
- **Unknown with a reason.** No proximity data gives "OpenStreetMap data not loaded yet"; a lot with no beach, park or
  shop of a kind in the loaded data gives "No ocean beach found in the OpenStreetMap data loaded" and so on.
- The 800 m in the park-area field is the pipeline's default radius; the report text names it, so changing the pipeline
  setting means changing the report text too.

## Consequences
- The suburb card still shows beach, parks and shopping as unknown; suburb-level roll-ups are a later PR.
- Real walking routes replace the estimate when a routing engine is added (a later PR); the report fields already allow it
  (`walkingM`, `walkingMinutes`).
