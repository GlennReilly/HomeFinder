# 0042. Empty DEM tiles are no data

Status: Accepted

## Context
Where no LiDAR reaches, the Geoscience Australia service answers a request with a sparse GeoTIFF (about 1,194 bytes, 35 of the 96 tiles
downloaded for the pilot area). PostGIS reads it as every pixel exactly 0 with no no-data value declared, and the loader (ADR 0036) only
treated heights outside -50 to 1500 m as no data, so 0 counted. The result: 10,690 of 171,082 lots (6%) had a stored height of 0.0 m, and
10,681 a slope of 0%, shown on the Lot Card as real figures. The drainage tool (ADR 0040) already treats these tiles as no data.

## Decision
- `load-dem.sh` drops, before the terrain SQL runs, every tile whose pixels are all exactly 0 (minimum and maximum both 0). A real tile can hold
  zeros (sea level, 0.0 m beside the coast) but not only zeros. Lots that only such a tile reaches get no `lot_terrain` row, so the card shows
  Unknown with "No elevation data covers this lot".
- The script says how many empty tiles it skipped. If every tile is empty it stops before touching the existing results.
- ADR 0036 stands otherwise; this adds a rule to its loader.

## Consequences
- About 10,700 lots change from 0.0 m and 0% to Unknown once `load-dem.sh` is re-run. Lots on the edge of a real tile keep their figures.
- A genuine tile that is exactly flat at 0 m everywhere (open sea) would be dropped too; no lot sits there.
- Heights near the coast that are really 0.0 m inside a real tile are kept.
