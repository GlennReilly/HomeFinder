# 0081. Class names as named constants

Status: Accepted

## Context

HTTP verbs, ARIA names and the card row classes already live in named constants (`HttpVerb`, `Aria`, `CardRowClasses` in `HtmlNames.kt`). The HTML builders and `webApp/Main.kt` still spelt about 70 other class names inline (`p("hint")`, `button(classes = "close")`, `querySelector(".highlighted")`), so a rename in `styles.css` meant a text search and the same name was repeated across files.

## Decision

Every class name a Kotlin UI file sets or selects is a `const val` in `HtmlNames.kt`, grouped by area: `CardClasses`, `RatingClasses`, `NaplanClasses`, `FilterClasses`, `ListingLinkClasses`, `SearchClasses`, `LayoutClasses` (beside the existing `CardRowClasses`, `DetailsFold`, `SchoolLines`). Compound class strings are built from other constants in the same const expression. The string values are unchanged, so `styles.css`, the e2e selectors and the HTML-asserting unit tests need no change. Map-style property names and element ids are out of scope.

## Consequences

- The rendered HTML is byte-identical; the existing tests pass unchanged and still pin the values.
- A class is renamed in one place plus `styles.css`.
- New UI code should add its classes here rather than inline.
