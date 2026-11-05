# 0057. Selecting a suburb changes only that suburb

Status: Accepted (supersedes the "other suburbs dim" part of 0017)

## Context
ADR 0017 tinted the selected suburb and dimmed every other suburb slightly. On the dense Newcastle and Lake Macquarie map the dim covers dozens of suburbs, so
tapping one looked as though every suburb had been selected, and the selected one was hard to tell from the rest.

## Decision
- No suburb other than the selected one changes colour. The suburb dim layer and its filter are removed.
- The selected suburb gets a stronger tint (purple, 30% opaque, up from 18%, so it reads on green land and on blue water in light and dark mode) and keeps its
  thick outline (3.5 px).
- The filter panel's own wash and match tint (ADR 0035, 0046) is a separate mechanism and is unchanged. At zoom 16 and above the other lots are still dimmed when a lot is
  selected: that does not tint suburbs and a lot is small enough to stand out.
- The map is exposed as `window.homefinderMap` so browser tests can read its layers and filters; nothing in the app uses it.

## Consequences
- Selecting a suburb is easy to read; the map is otherwise unchanged.
- The filter wash can still dim suburbs, but only while a filter is on and only by the filter's rules.
