# 0002. PostGIS in Docker Compose, Flyway migrations, .env credentials

Status: Accepted

## Context
PR 2 needs a local, migrated, empty PostGIS database. The spec asks for a migration tool and a credential approach.
The repo will be pushed to GitHub, so secrets must stay out of git. Android reaches the API (not the database) over
LAN or Tailscale.

## Decision
- Database: `postgis/postgis:18-3.6` (Postgres 18, PostGIS 3.6), data in a named volume.
- Migrations: Flyway (`flyway/flyway:13.11.1`) as a Compose service running plain SQL `V<n>__name.sql` files from
  `pipeline/db/migrations`. Applied migrations are never edited; changes are new migrations.
- Credentials: `pipeline/.env` (gitignored) with a committed `pipeline/.env.example`.
- The database port binds to `127.0.0.1` only.
- V1 enables PostGIS; V2 adds `pipeline_run` to record stage runs and input versions (idempotent, reproducible
  pipeline stages).

## Consequences
- No Gradle dependency is added; Flyway and PostGIS are Docker images only.
- No automatic rollback (Flyway Community); undo with a new migration.
- Plaintext password on the developer's disk, acceptable for a local database. Revisit (Docker secrets or a managed
  secret store) if the database is hosted. LAN or Tailscale access to data goes through the Ktor API (PR 7).
- Image tags were the latest stable found on Docker Hub on 2026-10-29 and are pinned.
