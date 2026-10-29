# 0012. Shared models and serialization

Status: Accepted

## Context
PR 6 defines the models shared by the server and every client (spec "Data model and API"). The spec gives
`Attribute<T>` and `PropertyReport` and leaves `SuburbReport`, the value types and their enum values open.

## Decision
- All models live in `shared` (`au.homefinder.shared.model`) and are `@Serializable` with kotlinx.serialization.
- Dates are `kotlinx.datetime.LocalDate`, written as ISO dates; kotlinx-datetime 0.8.0 is an `api` dependency of
  `shared` because it appears in the public models.
- Money is whole AUD dollars in `Long` fields named `...Aud`. Distances are `Int` metres and walking or driving times
  `Int` minutes. Shares are fractions from 0 to 1.
- Enum values: `Confidence` HIGH/MEDIUM/LOW/NONE; `WallMaterial` BRICK/DOUBLE_BRICK/BRICK_VENEER/OTHER;
  `FloodClass` NOT_MAPPED/LOW/MEDIUM/HIGH; `SlopeClass` FLAT/GENTLE/MODERATE/STEEP (flat under 5%, gentle 5 to under
  10, moderate 10 to 20, steep over 20); `FallDirection` TO_STREET/TO_REAR/TO_SIDE/FLAT; `LotShape`
  REGULAR/CORNER/BATTLE_AXE/IRREGULAR. Unknown is a null attribute value with confidence NONE, never an enum entry.
- `Attribute<T>` refuses a null value with any confidence but NONE, so "unknown" cannot masquerade as known.
- Every statistic in `SuburbReport` is an `Attribute<T>`, like `PropertyReport`.
- `ApiJson` is the one JSON configuration: nulls and defaults are always written (an unknown value is an explicit
  `"value": null`) and readers ignore fields they do not know, so a newer server does not break an older client.
- Value types validate their invariants on construction: ordered price ranges and block size percentiles, shares in
  0 to 1, slope mean at most maximum, bearing in 0 up to 360.

## Differences from the spec's sketch
- `flood` is `Attribute<FloodRisk>` (class plus optional AEP percent) because the spec wants both on one result.
- `parks` is `Attribute<ParkAccess>` (nearest parks plus park area within 800 m) for the same reason.
- `AspectResult` also carries `lotShape`, so ambiguity on corner, battle-axe and irregular lots is part of the data.

## Consequences
- Enum names are part of the wire format; renaming one is a breaking change for stored or cached data.
- The pipeline and API (PRs 13 to 20) fill these types; the field list may grow, which readers tolerate.
- `LocalDate` adds a library to every target, which Android and the JVM handle without extra setup.
