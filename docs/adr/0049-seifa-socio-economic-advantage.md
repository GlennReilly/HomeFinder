# 0049. Socio-economic advantage from SEIFA 2021

Status: Accepted

## Context
Open data about who lives in a suburb is a useful signal beside prices and amenities. The ABS publishes Socio-Economic Indexes for Areas (SEIFA) 2021 for every
suburb: four indexes, each as a score and a decile, with the usual resident population. The ABS says scores are on an arbitrary scale and recommends deciles or
percentiles, that the indexes describe an area's residents as a group rather than any household, and that small areas may get no score.

## Decision
- Use one index, the **Index of Relative Socio-economic Advantage and Disadvantage (IRSAD)**, shown as a **decile** (1 = most disadvantaged tenth of areas in Australia,
  10 = most advantaged). The score (average 1,000) and the population are kept and shown in the row's note, not as the headline.
- Source: "Suburbs and Localities, Indexes, SEIFA 2021.xlsx" (Table 1), from the ABS SEIFA page, which the user downloads with `pipeline/scripts/download-seifa.sh`
  (the sandbox cannot). Creative Commons Attribution 4.0, credited "Based on Australian Bureau of Statistics data"; the licence wording was not on the pages read, so it
  follows the ABS's general terms and the ADR 0044 choice. The figures describe Census night 2021, so the row shows 2021-08-10.
- The `census` Gradle module gains a second tool (`:census:seifa`) that reads the workbook as zipped XML (as for the Census names), keeps NSW suburbs (code starts with 1)
  and writes a CSV; `pipeline/scripts/load-seifa.sh` loads it into `suburb_seifa` (migration V20) and matches ABS suburbs to ours by name, with the council area settling shared
  names, exactly as `load-census.sh` does.
- **Suburb Card**: a "Socio-economic advantage" row ("Decile 8 of 10 (10 is the most advantaged)") after "Sales behind the prices". Confidence follows the population: under
  200 residents low, under 1,000 medium, otherwise high.
- **Filter**: a fifth criterion, "Socio-economic advantage (SEIFA decile)", a 1 to 10 slider, with its own provenance and the same rules as the others (a suburb with no value
  never matches; a missing data set says so and shows no slider). The filters response gains `seifaDecile` and `seifa`.

## Consequences
- The decile is area-based: a high decile does not mean every household is well off, and neighbouring streets can differ. It is not comparable across Census years.
- Suburbs the ABS did not score (too few residents) have no row and never match the filter.
- The other three indexes (disadvantage only, economic resources, education and occupation) are in the file and can be added later without a new download.
- Crime (BOCSAR) is a separate decision: its file is large, its layout is not yet seen, and its counts need a population to become rates.
