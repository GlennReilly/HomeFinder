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
