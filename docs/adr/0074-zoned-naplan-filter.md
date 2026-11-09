# 0074. A suburb filter on the NAPLAN figures you typed for the zoned schools

Status: Accepted

## Context
ADRs 0068 to 0073 let you type one NAPLAN figure per school: a signed whole-number difference from the national average, in points, kept in `school_naplan` by
school code, with no year and no note. Most schools will never have one. You asked for a NAPLAN suburb filter, next to the zoned-schools ICSEA filter (ADR 0062)
in the Schools group of the Filters tab (ADR 0066).

## Decision
- **Criterion `naplan`, "Zoned schools' NAPLAN (vs national)"**, an "at least" slider in steps of 1 point (ADR 0052), in the Schools group after the two ICSEA
  criteria. Values and limits show their sign ("+11", "-10", "0").
- **A suburb's value** is the average of your figures for the zoned schools the Suburb Card's Schools row lists (ADR 0062, 0073: per level every zone covering at
  least a quarter of the suburb's lots, else the level's biggest zone), **weighted by the share of the suburb each zone covers**, leaving out listed schools with no
  figure, rounded to the nearest point (halves upwards). The listing rule is the card's own code (`SuburbReportFactory.listed`), so the filter and the card cannot
  drift apart; the zoned-ICSEA filter's SQL (quarter rule only, no fallback) is unchanged.
- **Unknown:** a suburb where no listed school has a figure has no value; it is counted in the criterion's unknown count and never matches while the filter is on
  (ADR 0046).
- **Range:** the actual lowest to highest suburb value, rounded outward to whole points; the slider starts at the lowest, so switching it on excludes only the unknowns.
- **Empty state:** once the catchments are loaded and the `school_naplan` table exists but no suburb has a value, the criterion is drawn with a disabled checkbox and
  slider reading "No NAPLAN figures typed yet". Without the catchments it says they are not loaded, as the zoned-ICSEA filter does.
- **Coverage in plain view:** the help line ends "N of M suburbs have at least one". The panel's one Details section (ADR 0072) adds a note that these are your own
  figures, that a value may rest on one school, and that a suburb with none never matches; the source line names the typed figures and the catchments, dated by the
  last figure saved.
- **Computed per request, never cached:** the server reads every suburb's zone shares joined to `school_naplan` on each `GET /v1/suburbs/filters` and averages them in
  Kotlin (`ZonedNaplan`). No migration, no load script. The response carries `Cache-Control: no-store`. After a figure is saved or cleared, `MapViewModel.naplanVersion`
  goes up and the page reloads the filter values, keeping the switched-on filters (limits clamped into the new ranges, a criterion left with no values switched off)
  and the open groups.
- The response gains `zonedNaplan` per suburb and a `naplan` provenance; older clients ignore both.

## Consequences
- The filter is only as good as what you have typed: with a handful of figures it filters a handful of suburbs, and a suburb can rest on one school. The help line
  and Details note say so.
- Primary and high schools are pooled, as in the zoned-ICSEA filter; a NAPLAN difference for a primary and one for a high school are averaged as if comparable.
- Reloading the panel after every save redraws it (one small request); a slider being dragged at that moment is put back to its carried value.
- No Suburb Card row shows the suburb's NAPLAN average; that can be added later without changing this rule.
