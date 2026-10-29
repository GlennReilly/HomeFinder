# 0005. Lots, suburbs, projected CRS and the address-to-lot link

Status: Accepted (link fallback extended by 0007)

## Context
PR 4 loads cadastre lots and official suburb boundaries for the pilot region. The spec left open whether to use a bulk
download or the web service, which target CRS to use, and (not in the PR 4 row) how lots relate to G-NAF addresses.

## Decision
- Data access: paged queries to the NSW Spatial Services web service (Suburb layer 2 of
  NSW_Administrative_Boundaries_Theme, Lot layer 8 of NSW_Land_Parcel_Property_Theme), 2,000 features per page. No
  bulk download and no GDAL dependency at pilot scale. Pages are cached in `data/raw/cadastre` and reused.
- Lots are fetched with one envelope query per pilot suburb and kept when their representative point lies inside a
  pilot suburb. Duplicates from overlapping envelopes are removed by `cadid`.
- Suburbs are matched by pilot locality name and must overlap an LGA polygon, so same-named suburbs elsewhere in NSW
  are ignored. Every pilot locality must have a boundary (strict by default).
- CRS: geometry is stored as supplied in GDA2020 geographic (EPSG:7844). Generated columns hold GDA2020 / MGA zone 56
  (EPSG:7856), which covers the pilot region (150 to 156 degrees E), and `lot.area_m2` is computed in 7856.
- Link: `address_lot` links each address to a lot by G-NAF `LEGAL_PARCEL_ID` when exactly one lot has that key,
  otherwise by the smallest lot containing the address point. `lot.parcel_key` is `lot[/section]/plan` with the "DP"
  prefix dropped and "SP" kept, matching G-NAF's format. The script reports linked, unmatched and "id match but point
  outside the lot" counts.

## Consequences
- Rebuilding suburbs clears lots and links; rebuilding addresses clears links. Rerun the later stages after either.
- About 9% of G-NAF addresses have no parcel id and rely on point-in-polygon; strata addresses may be unmatched
  where the cadastre has no matching lot polygon. These are counted, not hidden.
- The parcel key mapping was derived from sample data (G-NAF patterns and Hamilton lots) and is confirmed by the
  match rates on the first full load.
- Web service availability and schema are outside our control; cached pages make reruns independent of it.
