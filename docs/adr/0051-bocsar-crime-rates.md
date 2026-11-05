# 0051. Recorded crime per 1,000 residents from BOCSAR

Status: Accepted

## Context
The NSW Bureau of Crime Statistics and Research (BOCSAR) publishes recorded criminal incidents by suburb, offence and month (`SuburbData.csv`, Data.NSW, Creative Commons
Attribution, updated quarterly; 4,520 suburbs, 62 offence subcategories, Jan 1995 to Jun 2026, about 420 MB). The file has counts, not rates, and counts follow where an incident
happens, not where people live. SEIFA (ADR 0049) gives each suburb's usual resident population.

## Decision
- **Two measures**, each over the **latest 24 months in the file** (the window is read from the header: today Jul 2024 to Jun 2026, as of 2026-06-30):
  - *Violent crime*: every Assault subcategory (domestic, non-domestic, police), every Robbery subcategory (no weapon, firearm, other weapon) and both Sexual offences subcategories.
  - *Break-ins, car and home theft*: Break and enter dwelling, Motor vehicle theft, Steal from motor vehicle, Steal from dwelling and Malicious damage to property.
  Other offences (fraud, drugs, disorderly conduct, justice procedures and so on) are left out. The sets are named constants in `Crime.kt`.
- **Rate** = incidents / SEIFA usual resident population x 1,000, worked out in the server from `suburb_crime` and `suburb_seifa.population`, so the two loads can happen in either order.
- **Small areas**: under 500 residents the card still shows the rate but at low confidence and the filter treats the suburb as unknown (never matches). With no SEIFA population (the ABS
  "excluded areas") the card shows the count only ("1 incident in 24 months, no population to turn it into a rate") at low confidence, and the filter treats the suburb as unknown.
  Confidence is otherwise medium under 2,000 residents and high above.
- **Card**: two rows after "Houses and units": "4.2 per 1,000 residents (31 incidents in 24 months)". The row's note names the window and says crime is counted where it happens, so a suburb
  with a big shopping centre or business district looks worse than its residents warrant, and recorded crime also reflects reporting and policing.
- **Filter**: two more criteria, each an incidents-per-1,000 slider (step 1) with its own provenance and the usual unknown rules; the filters response gains `violentPer1000`, `propertyPer1000`
  and `crime`. Crime needs SEIFA's population, so without SEIFA loaded these criteria have no values and the panel says so.
- **Pipeline**: `pipeline/scripts/load-crime.sh` (the user runs it) streams the CSV with the `census` module's `:census:crime` tool into a small CSV and loads `suburb_crime` (migration V22).
  BOCSAR writes a name alone when it is unique in NSW and "Name (Council area)" when shared; a suburb of ours matches a name that appears once, or the tagged entry whose council area
  (`lga`) contains it. Anything still ambiguous is left unmatched and listed, never guessed.

## Consequences
- Rates swing with who counts as a resident: central Newcastle, Hamilton South and Jesmond read high (nightlife, retail, few residents) and outer suburbs low, which is the pattern the caveat warns of.
- Two years of data smooths single bad months but is not a trend; a suburb's rate can change a lot between releases when its count is small.
- A new BOCSAR release is loaded by deleting `data/raw/bocsar` and re-running `download-bocsar.sh` then `load-crime.sh`.
- Domestic violence is inside "violent"; a separate measure could be added later.
- Migration V22 follows V21 (the other SEIFA indexes); V21 must be merged first.
