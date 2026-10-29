# Pilot localities

Newcastle and Lake Macquarie (NSW). A suburb is included when it overlaps the City of Newcastle or City of Lake
Macquarie LGA polygon at all, so every address inside either council is loaded (border suburbs are loaded whole).

- Source: NSW Spatial Services, NSW_Administrative_Boundaries_Theme FeatureServer, layers 8 (LocalGovernmentArea)
  and 2 (Suburb), queried 2026-10-29, spatial relation "intersects".
- Count: 173 localities: 172 suburbs plus `LAKE MACQUARIE`, a G-NAF locality with 4 street addresses on the lake shore.
- `expect_addresses=false`: valid G-NAF localities with no current addresses (Frazer Park, Olney, Point Wolstoncroft).
- Match key against G-NAF is the locality name in NSW. Postcode is informational.
- `scripts/check-gnaf.sh` proves completeness against the real G-NAF data after each load.
