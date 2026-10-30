# 0038. Sales windows end on the latest sale

Status: Accepted

## Context
The Suburb Card said "from 53 house sales in the 12 months to 2026-10-26". That date was the day the sales files were downloaded
(ADR 0018 took "now" from the newest file date, so reruns give the same answer). But Valuer General sales reach the files weeks after
contracts are exchanged, so the newest sale was earlier than the date shown, and you reasonably doubted it.

## Decision
- The statistics' "as of" date, and the end of both 12-month windows, is the **latest contract date** among the sales that are counted
  (ignoring any dated after the newest download, which can only be a typing error). Migration V17 replaces `refresh_suburb_stats`
  with that one change. It is still taken from the data, not the clock, so reruns give identical rows.
- The card says "in the 12 months to the latest sale on file (2026-10-09)" and keeps the note that the latest weeks are thin.
- The download day stays in `sale.downloaded_on` and the pipeline run log; it is no longer the window's end.

## Consequences
- The windows end a little earlier than before, so medians and price changes can shift slightly after `refresh-suburb-stats.sh` is rerun.
- ADR 0018's sentence about the window ending on the file date is superseded for the as-of rule; its other rules stand.
