# 0018. Sales statistics pipeline (NSW Valuer General)

Status: Accepted

## Context
The Suburb Card needs a real median price, price change, sales count and block size (spec: PR 10). The only free,
authoritative source of NSW sale prices is the Valuer General's Property Sales Information (PSI). You chose to use it
for personal, non-commercial use, over the last 24 months, counting only residential full-price sales of one property,
matched to suburbs by locality name and postcode. PR 10 is split: 10a is the pipeline (this decision), 10b is the
server and card.

## Decision
- **Licence.** The PSI page states CC BY-NC-ND 4.0, and other copies of it say CC BY 4.0 or CC BY-ND, so we apply the
  strictest. Raw files (`data/raw/sales`, already gitignored) and the derived medians stay on this machine and its
  database, and are not redistributed or published. Sales rows never appear in the API, only suburb-level statistics,
  attributed to "NSW Valuer General Property Sales Information". If the app is ever shared or sold, ask
  valuationenquiry@property.nsw.gov.au for a PSI licence first. The data carries "no guarantee of completeness or
  accuracy", which is why every statistic is an `Attribute` with a source and a confidence.
- **Files.** Yearly zips for 2024 and 2025 plus the weekly zips of 2026 (Mondays from 5 January), because 24 months
  back from late 2026 starts in late 2024. `scripts/download-sales.sh` is run by the user (it sets a polite
  user-agent, pauses between requests, records a SHA-256 on first download and treats 404 as "not published yet").
  Only B (sale) records are read; the purchaser and vendor (D) records are never loaded.
- **Districts.** Newcastle (008) and Lake Macquarie (004), plus the neighbouring councils Maitland (005), Port Stephens
  (010), Cessnock (001) and Central Coast (259), because several pilot suburbs (Thornton, Williamtown, Doyalson) cross
  the boundary. This widens the earlier "two councils" answer. Matching is still by locality name plus postcode, so a
  same-named suburb elsewhere is not counted. The list is `pipeline/config/sales_districts.csv`.
- **Counting rules**, each kept as a recorded `exclusion_reason` on the `sale` row rather than a silent drop, applied in
  this order: `not_residential` (nature of property must be R), `part_interest` (percentage interest under 100),
  `multi_property` (the dealing number covers more than one property in the district), `no_price`, `low_price`
  (under $50,000, which are nominal transfers), `no_contract_date`, `no_suburb_match` (no single suburb with that
  locality and postcode). A strata lot number makes a sale a unit, otherwise a house. A sale repeated in a later file
  is counted once, the latest file winning; the key is district, property, contract date, price and dealing number.
- **Windows.** "Now" is the latest file download date in the data (not the clock), so reruns are repeatable. The
  current window is the 12 months to then by contract date (a sale exactly 12 months back belongs to the previous
  window) and the previous window the 12 months before it (a sale exactly 24 months back is outside both). Price
  change is the change in the median between the two windows.
- **Minimum samples.** A median needs at least 5 sales in its window, and a price change needs both windows to have
  one; with fewer, the counts are still stored so the card can say "only 3 sales". Thresholds are named values in
  `config/sales.env`.
- **Block size** comes from the cadastre, not the sales: median, 10th and 90th percentile of the area of lots that have
  a linked address, are not strata parcels (plan label SP or CP) and are at least 100 m². On the real data about
  3% of addressed lots are strata and ~1,550 have areas above 100,000 m² (farms, reserves), which would drag a plain
  mean; percentiles tolerate them and the strata filter removes unit blocks that share one parcel. A suburb needs
  at least 10 such lots.
- **Storage.** Migration V10 adds `sale` (typed, with the exclusion reason and source file) and `suburb_stats` (one row
  per suburb), filled by `refresh_suburb_stats()`; the sale table is rebuilt on each load so the stage is idempotent.

## Consequences
- Medians lag reality: sales reach the Valuer General weeks after the contract date, so the most recent weeks of the
  window are thin. The card should show the as-of date, which is stored.
- The yearly list in `config/sales.env` must be moved forward by hand as years pass.
- Part-interest, multi-property and non-residential sales are excluded, so counts are lower than the true number of
  transactions; this favours clean medians over completeness, as you chose.
- `suburb_stats` is rebuilt, never edited, so changing a rule means rerunning `load-sales.sh`.
