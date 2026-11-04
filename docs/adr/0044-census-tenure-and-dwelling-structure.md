# 0044. Share rented and the mix of houses and units from the 2021 Census

Status: Accepted

## Context
You asked the Suburb Card to show the share of the suburb that is rented and the split between houses and units. Sales counts cannot say
that: they describe what sold, not what stands. The ABS 2021 Census does, and publishes it free for suburbs.

## Decision
- **Source:** the ABS 2021 Census General Community Profile DataPack for Suburbs and Localities (SAL), NSW (a 34 MB zip), table **G37**,
  tenure and landlord type by dwelling structure, occupied private dwellings. Licence: Creative Commons Attribution 4.0 (the DataPack's own
  licence readme). Because the figures are derived, the attribution is "Based on Australian Bureau of Statistics data", and every derived row's
  note says so. `pipeline/scripts/download-census.sh` (run by you) fetches and unpacks it into `data/raw/census`.
- **A Kotlin tool, module `census`** (pure Kotlin, unit tested) reads G37 by column name and the suburb code-to-name lookup from the
  geography workbook inside the DataPack. There is no spreadsheet library, and the xlsx is zipped XML, so the shared strings and sheets are read with the
  JDK's StAX. `pipeline/scripts/load-census.sh` runs it and loads `suburb_census` (migration V19: plain counts).
- **Matching to our suburbs** is by name, ignoring the "(NSW)" or "(Newcastle - NSW)" tag the ABS adds. When a name is shared (167 of our 173 suburbs
  match uniquely; the other 6, such as Carrington, Mayfield and Maryland, do not), the tag must name the council area the suburb lies in
  (`lga` table); anything still ambiguous is left unmatched and listed, never guessed. Today only Newcastle and Lake Macquarie council areas
  are loaded, so a shared name in another area stays unmatched until its area is.
- **Two Suburb Card rows**, after "Sales behind the prices": **Homes rented** ("45% of homes are rented", rented dwellings over dwellings with a stated
  tenure) and **Houses and units** ("58% houses, 25% townhouses, 15% units": separate houses; semi-detached, row, terrace and townhouses; flats,
  units and apartments, over the four stated structures). Other dwellings such as caravans are in the base but not shown.
- **Confidence** follows the number of dwellings behind the figure (under 15 low, under 50 medium, as for sales). The as-of date is Census night, 10 August 2021.

## Consequences
- The ABS randomly adjusts small counts, so a table's totals can differ from the sum of its parts by up to about 16 dwellings (981 of 4,544 NSW suburbs for the
  structure columns). The bases are chosen so shares stay sensible (the tenure base is never below the rented count; the structure base is the sum of the
  stated structures) and the notes warn that a suburb with few homes is only roughly right.
- Census suburbs (SAL) are the ABS's approximation of suburbs and differ a little from the boundaries on the map.
- The figures are from 2021 and do not change until the next Census (2026 data is due in 2027).
- Pilot suburbs outside the loaded council areas whose names are shared stay unmatched until their council area is loaded.
