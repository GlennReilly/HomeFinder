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
scripts/load-gnaf.sh       # loads staging, rebuilds the address table, then runs scripts/check-gnaf.sh
tests/test_load_gnaf.sh    # fixture-based integration test (needs Docker)
```

See [ADR 0003](../docs/adr/0003-gnaf-pilot-region-and-loading.md). Data: G-NAF © Geoscape Australia licensed by the
Commonwealth of Australia under the Open G-NAF End User Licence Agreement.
