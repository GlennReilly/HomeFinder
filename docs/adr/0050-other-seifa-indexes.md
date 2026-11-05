# 0050. The other three SEIFA indexes on the Suburb Card

Status: Accepted

## Context
ADR 0049 loaded one SEIFA 2021 index (IRSAD, advantage and disadvantage) per suburb. The same ABS workbook holds three more: disadvantage alone (IRSD), economic
resources (IER) and education and occupation (IEO).

## Decision
- Store and show the **decile** of each (1 = lowest tenth of areas in Australia, 10 = highest); no scores. Migration V21 adds `irsd_decile`, `ier_decile` and `ieo_decile`
  to `suburb_seifa`, null where the ABS gives the suburb no score for that index (Table 6 lists such cases, some index by index).
- Three rows on the Suburb Card, straight after "Socio-economic advantage", each worded for its direction: "Disadvantage (SEIFA)" (10 is the least disadvantaged), "Economic
  resources (SEIFA)" (10 has the most resources) and "Education and occupation (SEIFA)" (10 is the highest). Source, date, confidence (by population) and attribution are as for the IRSAD row.
- The filter stays on IRSAD only.
- The card now has 16 rows; **Customise** hides any of them (ADR 0039).
- Loading: no new download; re-run `pipeline/scripts/load-seifa.sh` (migration V21 applies itself) to fill the new columns.

## Consequences
- Until the loader is re-run the three rows read "Unknown" (the columns are null), and the IRSAD row is unaffected.
- A longer card; the indexes overlap (all four draw on the same Census questions), so showing all four adds little new for most suburbs.
- Adding any of them to the filter later is one more `FilterCriterion` and response field.
