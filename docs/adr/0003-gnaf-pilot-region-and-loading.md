# 0003. G-NAF pilot region and loading approach

Status: Accepted (region filter superseded by 0004; name-only matching superseded by 0006)

## Context
PR 3 loads G-NAF addresses for the pilot region. The spec left the pilot region and the raw-data location open.
G-NAF has no council-area field. data.gov.au refuses automated fetches from the assistant's tools, so the
1.5 GB download is run by the user.

## Decision
- Pilot region: City of Newcastle and City of Lake Macquarie (NSW).
- Region filter: a committed list of 144 suburb names (`pipeline/config/pilot_localities.csv`), built from NSW Spatial
  Services LGA and suburb layers (suburb centroid inside the LGA). Match key is the locality name in NSW.
- Release: full G-NAF August 2026, GDA2020, PSV, pinned in `pipeline/config/gnaf-release.env`. Not G-NAF Core.
- Raw downloads live in the gitignored `data/raw/` at the repo root.
- `scripts/download-gnaf.sh` downloads and unzips. No publisher checksum exists, so the first download records a
  SHA-256 (trust on first use) and later runs must match it.
- `scripts/load-gnaf.sh` copies the NSW PSV tables into `stg.*` (columns taken from each file's header) and rebuilds
  `address` with `sql/gnaf_address.sql`: current principal addresses with a current default geocode. Each run
  writes a `pipeline_run` row with the release and zip checksum. Reruns give the same rows.
- Coordinates are stored as supplied, GDA2020 geographic (EPSG:7844). Target CRS for projected work is decided in PR 4.
- Street type and flat type codes are stored raw; expanding them to words is deferred to the autocomplete PR.

## Consequences
- Verified so far only against a hand-made fixture. The real G-NAF column names, file layout and staging join keys
  are from memory of the G-NAF schema and are confirmed only when the real zip is loaded.
- Suburbs straddling the LGA boundary are included or excluded by centroid, so a few edge addresses may be missing.
- Licence: CC BY 4.0 based EULA. Attribution text must appear in the app (PR 25), and the data must not be used to
  generate addresses for mail.
- Next release (November 2026) is adopted by changing the pinned URL and rerunning.
