# pipeline

Local PostGIS plus Flyway migrations. Later PRs add the SQL, GDAL and tiling stages.

## Run

Create `pipeline/.env` from `.env.example` and set your own `POSTGRES_PASSWORD` (the file is gitignored), then:

```bash
docker compose up
```

The database listens on `127.0.0.1` only (set `POSTGRES_PORT` to change the port). Migrations live in
`db/migrations` (`V<n>__name.sql`); never edit an applied migration, add a new one.
See [ADR 0002](../docs/adr/0002-postgis-flyway-and-credentials.md).

## G-NAF addresses (PR 3)

```bash
scripts/download-gnaf.sh   # about 1.5 GB into ../data/raw (gitignored), checksum recorded
scripts/load-lga.sh        # council polygons, needed by the completeness check
scripts/load-suburbs.sh    # suburb boundaries (PR 4); G-NAF needs these to tell same-named suburbs apart
scripts/load-gnaf.sh       # loads staging, rebuilds the address table, then runs scripts/check-gnaf.sh
tests/test_load_gnaf.sh    # fixture-based integration test (needs Docker)
```

See [ADR 0003](../docs/adr/0003-gnaf-pilot-region-and-loading.md). Data: G-NAF © Geoscape Australia licensed by the
Commonwealth of Australia under the Open G-NAF End User Licence Agreement.

## Suburbs, lots and address links (PR 4)

```bash
scripts/load-lots.sh           # cadastre lots, paged per suburb (cached in ../data/raw/cadastre)
scripts/link-address-lots.sh   # links each address to a lot and prints match rates
tests/test_lots.sh             # fixture-based integration test (needs Docker and jq)
```

See [ADR 0005](../docs/adr/0005-lots-suburbs-crs-and-address-link.md). Source: NSW Spatial Services.

## Sales and suburb statistics (PR 10a)

```bash
scripts/download-sales.sh       # Valuer General yearly + weekly zips into ../data/raw/sales (run it yourself)
scripts/load-sales.sh           # applies the counting rules, rebuilds suburb_stats and prints what was excluded and why
scripts/refresh-suburb-stats.sh # rebuilds suburb_stats alone (for example after reloading lots)
tests/test_sales.sh             # fixture-based integration test (needs Docker and jq)
```

Rules, windows, thresholds and the licence limits (personal non-commercial use, no redistribution) are in
[ADR 0018](../docs/adr/0018-sales-statistics-pipeline.md); thresholds and file lists in `config/sales.env`, councils in
`config/sales_districts.csv`. Source: NSW Valuer General, Property Sales Information.

The sales figures' "as of" date is the latest contract date in the data (migration V17; before it was the download day, which overstated
how recent the sales are). The 12-month windows end on it, and sales reach the Valuer General weeks after exchange, so the last weeks are thin.
After pulling V17, run `scripts/refresh-suburb-stats.sh` to rebuild the figures.

## OpenStreetMap proximity (PR 13a)

```bash
scripts/download-osm.sh   # beaches, coastline, parks, supermarkets and malls from Overpass into ../data/raw/osm (run it yourself)
scripts/load-osm.sh       # builds osm_feature, then the nearest beach, parks and shops and the park area for every lot
tests/test_osm.sh         # fixture-based integration test (needs Docker and jq)
```

Needs suburbs and lots loaded first. Rules and thresholds are in [ADR 0023](../docs/adr/0023-osm-proximity-pipeline.md) and
`config/osm.env`. Source: OpenStreetMap contributors (ODbL).

`load-osm.sh` also rebuilds the per-suburb roll-up (distance to the nearest ocean beach from the centre and the nearest edge, park share, shopping centres and supermarkets inside or within 2 km): [ADR 0033](../docs/adr/0033-suburb-osm-rollup.md).

## Height and slope (DEM)

```bash
scripts/download-dem.sh   # the 5 m LiDAR DEM for the pilot region from Geoscience Australia's WCS, as GeoTIFFs in ../data/raw/dem (run it yourself)
scripts/load-dem.sh       # loads the GeoTIFFs and works out each lot's height and slope into lot_terrain (migration V16)
tests/test_terrain.sh     # integration test on a synthetic DEM and a stand-in WCS (needs Docker)
```

Source: Geoscience Australia, "DEM of Australia derived from LiDAR 5 Metre Grid" (CC BY 4.0). The download asks the service (which answers only in degrees, in GDA94)
for 0.05 degree squares (about 4.7 by 5.5 km) that touch a suburb; the loader resamples them to a 5 m metre grid before measuring slope. The service's one coverage is named `1`;
if it ever offers several, the script lists them and you set `DEM_COVERAGE`. Slope is rise over
run between neighbouring pixels (percent), averaged over the lot's pixels. See [ADR 0036](../docs/adr/0036-lot-height-and-slope-from-dem.md).
Tiles the service returns empty (no LiDAR there: every pixel exactly 0) are skipped and counted, so those lots show Unknown rather than 0.0 m
([ADR 0042](../docs/adr/0042-empty-dem-tiles-are-no-data.md)).

## Height above drainage

```bash
scripts/load-drainage.sh [dem dir] [stream ha]   # works out each lot's height above its nearest drainage line from the DEM into lot_drainage (migration V18)
tests/test_drainage.sh                           # integration test on a synthetic valley (needs Docker, xxd and a JDK 17)
```

Needs `load-lots.sh` and the GeoTIFFs from `download-dem.sh`. It exports each lot's representative point, runs the Kotlin tool in the `drainage`
module through Gradle (`./gradlew :drainage:run`), and loads its CSV. A stream starts where `DRAINAGE_STREAM_HA` hectares (default 5, `config/drainage.env`)
drain through one pixel. The DEM directory path must not contain spaces. Tiles that are empty or cut short count as no data and are reported once. It is an indication of
where water runs, not a flood result: see [ADR 0040](../docs/adr/0040-height-above-drainage-hint.md), which also records how it was checked.

## Census: share rented and houses vs units

```bash
scripts/download-census.sh [dir]   # the ABS 2021 Census DataPack for suburbs, NSW (34 MB zip), unpacked into ../data/raw/census (run it yourself)
scripts/load-census.sh [dir]       # works out and loads suburb_census (migration V19); lists suburbs it could not match
tests/test_download_census.sh      # download script against a local stand-in (no Docker)
tests/test_census.sh               # integration test on a tiny DataPack (needs Docker, python3 and a JDK 17)
```

The Kotlin tool in the `census` module reads table G37 and the geography workbook; the script loads its CSV and matches Census suburbs to ours by name,
using the council area (`lga`) to tell shared names apart. Data: Australian Bureau of Statistics, Census of Population and Housing 2021, Creative Commons
Attribution 4.0; derived figures are credited "Based on Australian Bureau of Statistics data". See [ADR 0044](../docs/adr/0044-census-tenure-and-dwelling-structure.md).

## SEIFA: socio-economic advantage

```bash
scripts/download-seifa.sh [dir]   # the ABS SEIFA 2021 suburb workbook (7 MB), into ../data/raw/seifa (run it yourself)
scripts/load-seifa.sh [file]      # loads suburb_seifa (migration V20); lists suburbs it could not match
tests/test_download_seifa.sh      # download script against a local stand-in (no Docker)
tests/test_seifa.sh               # integration test on a tiny workbook (needs Docker, python3 and a JDK 17)
```

The Kotlin tool in the `census` module reads Table 1 of "Suburbs and Localities, Indexes, SEIFA 2021.xlsx" (NSW suburbs only: the first digit of the suburb code is
the state); the script loads the Index of Relative Socio-economic Advantage and Disadvantage decile and score and matches ABS suburbs to ours exactly as the Census
load does. Data: Australian Bureau of Statistics, SEIFA 2021, Creative Commons Attribution 4.0; credited "Based on Australian Bureau of Statistics data". See
[ADR 0049](../docs/adr/0049-seifa-socio-economic-advantage.md).

## Schools (ICSEA)

```bash
scripts/download-schools.sh   # the NSW public schools master dataset (CSV, CC BY) into ../data/raw/schools (run it yourself)
scripts/load-schools.sh       # loads it and finds each school's pilot suburb
tests/test_schools.sh         # fixture-based integration test (needs Docker)
```

Source: NSW Department of Education, "NSW public schools master dataset" (Creative Commons Attribution); the ICSEA value is ACARA's.
See [ADR 0034](../docs/adr/0034-suburb-schools-and-icsea.md).

## BOCSAR crime (download only so far)

```bash
scripts/download-bocsar.sh [dir]   # the BOCSAR recorded-crime-by-suburb zip (over 10 MB), unpacked into ../data/raw/bocsar; prints each file's first lines (run it yourself)
tests/test_download_bocsar.sh      # download script against a local stand-in (no Docker)
```

Only the download exists: the file's layout has not been seen yet, so there is no loader. Data: NSW Bureau of Crime Statistics and Research on Data.NSW, Creative Commons Attribution.
