# 0016. Suburb lookup and report endpoints

Status: Accepted

## Context
Selecting a suburb on the map needs to know which suburb is under a tap and to show a card for it (spec: suburb
selection, PR 9). You chose to find the suburb on the server, show name, postcode, area and every spec row as unknown,
and keep search for PR 11. PR 9 is split: 9a is the server side (this decision), 9b is the client.

## Decision
- `GET /v1/suburbs/at?lat=&lon=` returns a `SuburbRef` (id, name, postcode) for the suburb whose full-detail boundary
  covers the point, using `ST_Covers` so a point on a boundary counts. If boundaries overlap the smallest suburb wins.
  Latitude and longitude must be finite and in range, otherwise 400; no suburb is 404; no or a failing database is 503.
- `GET /v1/suburbs/{suburbId}` returns a `SuburbReport`; an unknown id is 404. Both need the API key (header only; the
  query-parameter key stays limited to tiles).
- `SuburbReport` gains `areaKm2: Attribute<Double>`. Name, postcode and area come from the boundary data (area is known,
  confidence HIGH). Every statistic is an explicit unknown with the source that will supply it and the note "Not
  computed yet", so the card already has its final shape and nothing is a silent blank. Later pipeline PRs replace
  the unknowns one at a time.
- The lookups are behind a `SuburbDirectory` interface (PostGIS in production, a fake in route tests) and the report is
  built by a pure `SuburbReportFactory`.

## Consequences
- A tap needs the API, as the map tiles already do. Hit-testing against the full boundary means edges are exact, not
  approximated from simplified tiles, and Android and both desktop renderers share one tested implementation.
- Suburb ids are the NSW boundary `cadid` strings and are passed as bind parameters, never concatenated.
- Verified on the real 173 suburbs: 200 random real addresses all resolved to the suburb named in their own G-NAF
  record, at about 12 ms a request.
- `SuburbReport` changed shape (a new required field), which is fine because nothing consumed it yet.
