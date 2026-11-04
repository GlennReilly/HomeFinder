# 0040. Height above drainage as an indicative hint

Status: Accepted

## Context
Flood risk is still Unknown on both cards (ADR 0036): the state flood-planning layer has no features for Newcastle, Lake Macquarie or
Maitland, and the council studies have uneven licences. You asked whether height above sea level is a good enough proxy. It is not: it
ignores whether a lot is beside a creek or on a ridge at the same height. **Height above nearest drainage** (HAND) does: the lot's
ground height above the first stream its water reaches, worked out from the 5 m DEM already loaded.

A prototype was checked against the one council flood product found that covers ELERMORE VALE: the City of Newcastle's *City-wide
Floodplain Risk Management Study and Plan, Compendium of Maps* (BMT WBM for Newcastle City Council, June 2012; CC BY 4.0; a 663 MB PDF of
maps, not spatial data; it is kept in `data/raw/flood`, git-ignored, and its cross-reference table is damaged but Ghostscript repairs it).
Map 1-B shows 10% annual-chance flash flood depths over Wallsend and Elermore Vale. The map has no coordinates, so it was placed by its
scale bar (north up) with one offset, found by overlaying its flood corridor on the DEM's low-lying pixels; the placement was then checked
by drawing the cadastre's lot outlines over the aerial photo, where they follow the streets and houses. Of the 1,755 ELERMORE VALE lots
fully inside the mapped area, 76 (4.3%) have at least 5% of their area inside the corridor:

| Height above drainage | Lots | In the corridor |
| --- | --- | --- |
| under 1 m | 79 | 43% |
| 1 to 2 m | 66 | 18% |
| 2 to 5 m | 285 | 9% |
| 5 to 10 m | 434 | 0.9% |
| over 10 m | 891 | 0% |

Ranking lots by it finds the corridor lots better than height above sea level does (AUC 0.93 against 0.84; of the 79 lowest lots by
height 21 are in the corridor, of the 79 under 1 m above drainage 34 are). Above 5 m only 4 of 1,325 lots touch the corridor.

## Decision
- **A new JVM module `drainage`** (pure Kotlin, no framework or GDAL) does the work: a priority flood fills pits so every pixel drains
  off the window, flow accumulation counts the ground draining through each pixel, a stream is any pixel draining at least **5 ha**
  (`pipeline/config/drainage.env`), and a lot's result is its ground height above the first stream pixel its path meets, sampled at the
  lot's representative point, with the size of that stream's catchment. The JDK's TIFF reader reads the DEM tiles; their few header tags are
  parsed directly so a cut-short tile opens and is reported (as no data) instead of stopping the run. It could not be done in the PostGIS
  image: there is no GDAL there and a priority flood is not a set operation.
- `pipeline/scripts/load-drainage.sh` exports the lots' points, runs the tool through Gradle (blocks of 0.08 degrees with a 0.02 degree
  margin, so no window passes about 5 million pixels), and loads `lot_drainage` (migration V18, only per-lot results). Full pilot run: 171,082
  lots in 38 seconds.
- The **Lot Card gets a "Height above drainage" row**, next to the height above sea level: "Very close to a drainage line, 0.4 m above a stream
  draining 21 ha". Classes: very close under 1 m, close 1 to under 2 m, some way above 2 to under 5 m, well above from 5 m (the bands
  above). Confidence is **LOW** and the note says it is not a flood result. **Flood stays Unknown.**
- No suburb roll-up, filter or map layer yet.

## Consequences
- **Validated in one suburb against one event.** ELERMORE VALE is hilly with creeks; rarer floods spread wider than the 10% corridor;
  the council map is from 2012. Flat low-lying land is not checked: across the whole pilot 18.5% of lots come out under 1 m (against 4.5% in
  ELERMORE VALE), so on flat ground the hint may flag broad areas. A second suburb on the flat side should be compared before the row is
  trusted there.
- It ignores drains, culverts and levees, flash flooding from ponding, and anything built since the LiDAR surveys (2001 to 2015).
- The 5 ha stream size is a choice, not a measurement; a lot drains to a 5 ha gully or a 500 ha creek and the card says which.
- 158,015 of 171,082 lots get a result. The rest have no DEM tile (see below) or their water leaves the window before meeting a stream; the card
  says "no result" and why.
- **An existing problem found on the way:** 10,649 lots (6%) have a stored height of exactly 0.0 m in `lot_terrain`. They lie in tiles that
  the service returned empty (1,194-byte sparse GeoTIFFs: no LiDAR there), which the DEM loader reads as zero metres rather than no data,
  so the lot card shows "0.0 m" and slope 0% for them. The drainage tool treats those tiles as no data. The height and slope loader should
  do the same: a separate change.
- Attribution (CC BY 4.0): Geoscience Australia for the DEM; Newcastle City Council and BMT WBM for the flood study used to check it.
- Running it needs a JDK 17 and Gradle on the machine that runs the pipeline (the same as for building the app).
