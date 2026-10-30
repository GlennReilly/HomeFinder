# 0036. Lot height and slope from the 5 m DEM

Status: Accepted

## Context
You asked for height above sea level, slope and aspect. Flood data is a separate gap: the state flood planning layer has no features for
Newcastle, Lake Macquarie or Maitland, so height is only a hint there, never a flood result. The elevation sources found:
- **Geoscience Australia's national 5 m DEM**, derived from LiDAR surveys between 2001 and 2015, Creative Commons Attribution 4.0. It
  covers populated coastal areas but is not continuous. It is offered as downloads by UTM zone (the NSW zone 56 file is 296 MB), and as
  a WMS and a WCS (catalogue record: https://ecat.ga.gov.au/geonetwork/srv/api/records/22be4b55-2465-4320-e053-10a3070a5236).
- Finer 1 m and 2 m LiDAR through ELVIS (emailed archives, large files), and a NSW foundation elevation model whose licence page
  shows "not specified".
The PostGIS image has raster support but no `raster2pgsql` or GDAL tools, and adding an image for them would be a new dependency.

## Decision
- Use the 5 m DEM through its WCS. `download-dem.sh` (run by you) asks for 5 km squares that touch a pilot suburb, in the MGA zone 56 grid,
  and keeps each as a GeoTIFF; `load-dem.sh` sends each file into PostGIS (`ST_FromGDALRaster`, GTiff only), tiles it, and works out per lot
  into `lot_terrain` (migration V16): mean height, mean and maximum slope, and the pixel count. Only these per-lot results are kept.
- Slope is rise over run in percent from each pixel's 3 by 3 neighbourhood, so a strip one pixel wide along every 256 pixel tile edge has
  none and is left out. A lot's figures use the pixels whose centre lies inside it; a lot too small for a pixel centre takes the pixel under
  its representative point. Heights outside -50 to 1500 m are treated as no data.
- The DEM is GDA94 and the cadastre GDA2020; the 1.8 m difference is under half a pixel and is ignored, and recorded here.
- The lot card shows **Height above sea level** ("35.0 m") and **Slope** ("Moderate, 12.0% on average (steepest 21.3%)"), confidence
  MEDIUM, with the source, the load date and a note that the ground is bare earth from surveys up to 2015. A lot with no coverage is
  Unknown with that reason; before any load the reason is "Elevation data not loaded yet".
- **Which way a lot falls (and any aspect) is not done here.** The card's backyard aspect is defined from the dwelling to the rear boundary,
  which needs the street side of each lot; the slope's `fall` field stays empty (now optional in the model).
- The download step is tested only against a stand-in WCS, because the real service could not be reached when this was written. If its
  address, coverage name or parameters differ, the script says what it got and `DEM_WCS_URL` / `DEM_COVERAGE` are the settings to change.

## Consequences
- Height is not a flood measure and the card does not present it as one; flood stays Unknown until a council source is loaded.
- LiDAR surveys end in 2015: new estates and earthworks since then are not in the model.
- The vertical reference of the WCS model is not stated in its catalogue record; the figures are described as height above sea level, and
  this should be checked against a known benchmark when the real data is loaded.
- Attribution (CC BY 4.0): the card names Geoscience Australia; the README repeats it.
