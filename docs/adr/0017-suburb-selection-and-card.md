# 0017. Suburb selection and the Suburb Card

Status: Accepted

## Context
PR 9b lets a user select a suburb and see a card for it. The spec says selection is held by suburb id in the shared view
model so search, map tap and shortlist all select the same way, that the selected suburb gets a thick highlight with the
rest of the map dimmed, that Back or Escape clears it, and that the card is a bottom sheet on phones and a side panel on
desktop and web. Your answers for PR 9: layout chosen by window width, hit-test on the server (ADR 0016), search in PR 11,
and a card showing name, postcode, area and every other row as unknown.

## Decision
- State: `MapViewModel` holds a `MapUiState` (selected suburb id, the card state, an optional notice) in a `StateFlow`.
  `select(suburbId)` is the one entry point, used by a tap now and by search and the shortlist later. The latest
  selection wins if answers arrive out of order, and `clear()` also cancels a request in flight.
- Taps: every renderer reports a `MapTap` (latitude, longitude, zoom). At zoom 16 and above a tap does nothing, because a
  tap there will select a lot (PR 12). Below that the app asks `GET /v1/suburbs/at`; a tap on no suburb clears the
  selection, and a failed lookup keeps the selection and shows a short notice.
- Highlight: the selected suburb gets a tinted fill and a thick outline, and the other suburbs dim slightly. Android uses
  MapLibre fill and line layers filtered on `suburb_id`; the desktop overlay keeps each suburb's own shape and draws the
  same thing.
- Card: below 600 dp wide it is a bottom sheet (peeks, can be dragged, swiping it away clears the selection); at 600 dp
  and wider it is a 360 dp panel on the right, laid over the map. It shows the name and postcode at once from the tap,
  then the report: area known, every other row "Unknown" with its source and "Not computed yet". Failures show a message
  and Try again.
- Clearing: the card's Close button, swiping the sheet away, Back on Android (`BackHandler`) and Escape on desktop.

## Findings
- The Android app targets API 36, where Back is delivered through the predictive-back callback. Without
  `android:enableOnBackInvokedCallback="true"` in the manifest the app's `BackHandler` never ran (the device log said so),
  so Back did nothing. The flag is now set. This only showed up on a device, not in tests.
- The status badge can report a timeout on the first request after an emulator's cold boot; tapping it recovers.

## Consequences
- A tap needs the API (as the tiles already do) and a key; without a key there is no selection.
- Selection survives neither a rotation nor a process restart yet; it is held in memory.
- Names are uppercase because the NSW boundary data is.
