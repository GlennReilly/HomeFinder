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

## OpenStreetMap proximity (PR 13a)

```bash
scripts/download-osm.sh   # beaches, coastline, parks, supermarkets and malls from Overpass into ../data/raw/osm (run it yourself)
scripts/load-osm.sh       # builds osm_feature, then the nearest beach, parks and shops and the park area for every lot
tests/test_osm.sh         # fixture-based integration test (needs Docker and jq)
```

Needs suburbs and lots loaded first. Rules and thresholds are in [ADR 0023](../docs/adr/0023-osm-proximity-pipeline.md) and
`config/osm.env`. Source: OpenStreetMap contributors (ODbL).

`load-osm.sh` also rebuilds the per-suburb roll-up (distance to the nearest ocean beach from the centre and the nearest edge, park share, shopping centres and supermarkets inside or within 2 km): [ADR 0033](../docs/adr/0033-suburb-osm-rollup.md).
