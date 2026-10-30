# 0023. OpenStreetMap proximity pipeline

Status: Accepted

## Context
The spec's property attributes include the distance to the beach, nearby parks and nearby shops, all from OpenStreetMap.
You chose straight-line distances for now (walking routes come with a routing engine later) and fetching the data from
the Overpass API for the pilot area. PR 13 is split: 13a is the pipeline (this decision), 13b is the server and lot card.

## Decision
- **Fetching.** `scripts/download-osm.sh` (run by you, polite and one request at a time) asks Overpass for four things
  and saves each answer in `data/raw/osm`: beaches (`natural=beach`) and the coastline (`natural=coastline`) in a box
  0.3 degrees (about 33 km) around the loaded suburbs, and parks (`leisure=park`, `landuse=recreation_ground`) and
  supermarkets and malls (`shop=supermarket|mall`) in a box 0.1 degrees (about 11 km) around them. Existing files are kept.
- **Loading.** `scripts/load-osm.sh` turns the answers into `osm_feature` with PostGIS: nodes are points, closed ways
  polygons, other ways lines, and a relation becomes the area its member lines enclose (`ST_BuildArea` of the merged
  lines, so holes work). OSM's WGS84 is stored as GDA2020 (about 1.8 m apart, ignored). The import time and the age of
  the OSM data (`osm3s.timestamp_osm_base`) are kept in `osm_import` and become the as-of date.
- **Rules**, all named in `config/osm.env`: a park must be a polygon of at least 5,000 m² (0.5 ha, spec); a beach counts as
  an ocean beach only when it lies within 150 m of the mapped coastline, which drops lake and river beaches; a mall or
  supermarket is any such feature; malls and supermarkets are kept as points or areas.
- **Per lot** (`refresh_lot_proximity`, migration V11), from each lot's representative point: the nearest beach, the
  three nearest parks, the nearest supermarket and the nearest mall (`lot_nearest`: kind, rank, name, straight-line
  metres to the feature's edge, zero inside a park), and the park area within 800 m (`lot_amenity`, summed, so parks that
  overlap are counted twice). Nearest features use each kind's own spatial index with a few extra candidates re-ordered
  by true distance, because the index orders by bounding box.
- Distances are straight lines. A walking time, where shown, is an estimate made on the server from the straight line
  (13b), never a route.

## Consequences
- Measured on your 171,082 lots with synthetic features (2,500 parks, 12 beaches, 150 supermarkets, 25 malls),
  `refresh_lot_proximity` takes about 2 minutes 13 seconds.
- The beach rule is a heuristic: an estuary or harbour beach on the mapped coastline counts as an ocean beach, and an
  unmapped beach is missed. The card must say so (13b: medium confidence).
- OSM is ODbL (share-alike, attribution), fine for private use; the map already carries the attribution.
- Transport (needs a Transport for NSW key), schools, flood, slope and aspect are separate pipelines.
