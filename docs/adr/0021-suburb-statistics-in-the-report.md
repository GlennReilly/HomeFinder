# 0021. Suburb statistics in the report

Status: Accepted

## Context
PR 10a built `suburb_stats` from the Valuer General sales and the cadastre (ADR 0018). The Suburb Card must show those
numbers with the provenance the spec demands: source, as-of date and a confidence, and "unknown" with a reason when the
data is too thin or has not been loaded.

## Decision
- The server reads one `suburb_stats` row per report (`SuburbDirectory.stats`) and `SuburbReportFactory` turns it into
  the report's statistics. A missing `suburb_stats` table (before the first sales load) or a missing row means "no
  statistics", not an error; any other database failure is still a 503.
- **Values.** House and unit median price, the house price change over 12 months, the number of sales behind the prices
  (houses plus units in the latest 12 months) and the block-size spread (median, 10th, 90th percentile). Price change
  is for houses only because the report has one price-change field.
- **Source and date.** Sales values name "NSW Valuer General Property Sales Information" and carry the latest sales
  file date as their as-of date. Block size names the cadastre and has no date.
- **Confidence by sample size** (named constants): fewer than 15 samples is LOW, 15 to 49 MEDIUM, 50 or more HIGH. A
  price change uses the thinner of its two periods. The sales count is always MEDIUM because the files lag the true
  number of transactions; its note says so.
- **Unknown with a reason.** A median the pipeline withheld stays unknown with a note such as "Only 3 house sales in the
  12 months to 2026-06-01, too few for a median"; no sales data gives "Sales data not loaded yet"; no linked lots gives
  "Not computed yet". The server does not repeat the pipeline's minimum, so the notes never quote it.
- The sales source string changes from "NSW Valuer General" to the full dataset name.

## Consequences
- The card shows real prices as soon as `load-sales.sh` has run, with no further server change.
- Thresholds are judgement calls; they are constants in one place and tested at their edges.
- An unrefreshed `suburb_stats` is served as it stands; its as-of date shows how old it is.
