# 0037. DEM request in degrees, resampled to metres

Status: Accepted

## Context
ADR 0036 chose Geoscience Australia's 5 m DEM through its WCS, written before the service could be reached. The first real run and a
look at the service's own descriptions showed three things that ADR got wrong or did not know:
- The service's capabilities list its own name (`DEM_LiDAR_5m_2025`) next to the one coverage, whose id is `1`. Asking for the service name is
  refused (HTTP 400).
- The service answers only in degrees (EPSG:4326 or GDA94 geographic EPSG:4283), not in the MGA metre grid. Its pixels are
  0.000055063 by 0.000051601 degrees (about 5.1 by 5.7 m).
- A request for a cell returns an uncompressed 32-bit float GeoTIFF (about 4 MB for 908 by 969 pixels) with no no-data value declared.
  Fill pixels can be NaN or absurd values.
ADR 0036's choice of source, tables and card rows stands; this records the changed method.

## Decision
- `download-dem.sh` reads only the coverage list (not the service name) and, with the single coverage `1`, asks for 0.05 degree cells that
  touch a suburb, in EPSG:4283 at the service's own pixel size. A refused request shows the service's message (curl no longer hides the body).
- `load-dem.sh` loads each file as GDA94 degrees. The SQL first sets everything outside -50 to 1500 m (including NaN) to -9999 and declares it
  no data, then resamples each file bilinearly to a 5 m grid in MGA zone 56, tiles it and measures slope there, because slope in percent
  needs metres. Per-lot results are as in ADR 0036.
- The pixel size, cell size and both projections are settings in `config/dem.env`.

## Consequences
- Adjacent cells are resampled separately, so their 5 m grids are not aligned and a one pixel strip at each cell and tile edge has no slope. Lots
  on such an edge use the pixels they have.
- Bilinear resampling smooths the 5.1 by 5.7 m source slightly; slope is therefore a gentle estimate, which the card note already says.
- A real-data check is still owed: the heights of known places (for example a beach near zero, a known hill) against the loaded `lot_terrain`.
