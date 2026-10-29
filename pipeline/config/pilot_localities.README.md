# Pilot localities

Newcastle and Lake Macquarie (NSW). A suburb is included when it overlaps the City of Newcastle or City of Lake
Macquarie LGA polygon at all, so every address inside either council is loaded (border suburbs are loaded whole).

- Source: NSW Spatial Services, NSW_Administrative_Boundaries_Theme FeatureServer, layers 8 (LocalGovernmentArea)
  and 2 (Suburb), queried 2026-10-29, spatial relation "intersects".
- Count: 172 suburbs. The Lake Macquarie water body (no postcode) is excluded.
- Match key against G-NAF is the locality name in NSW. Postcode is informational.
- `scripts/check-gnaf.sh` proves completeness against the real G-NAF data after each load.
