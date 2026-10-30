# 0032. Browser smoke tests

Status: Accepted

## Context
The map and the page were only ever checked by hand, in a browser, after each change. Several regressions of that kind
(layers added on the wrong event, labels that vanished, an expression that stopped a layer loading) were found by looking.
You chose Playwright, with the API faked rather than a real server and database.

## Decision
- A new module `e2e` holds browser smoke tests written in Kotlin with Playwright for Java (the project is Kotlin-first, so
  there is no npm project to keep). They drive the **installed Chrome** (`E2E_BROWSER_CHANNEL`, default `chrome`; GitHub's
  runners have it), so no browser is downloaded and `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` is set.
- `FakeApp` serves the built web bundle and answers the API with a few fixed answers built from the shared models, so a change
  to the API contract breaks the tests. It insists on the API key and keeps the one Homely score the tests save. The one outside
  service the page uses, the OpenFreeMap basemap, is replaced by a plain background and empty fonts, so the tests never use
  the internet. Map tiles answer 204: the tests check the page and its behaviour, not the pictures.
- The tests cover: the key prompt and remembering the key; a tap on the map opening a suburb card (and Escape closing it);
  a wrong key being reported rather than crashing; search suggestions, opening a suburb, an address's lot card, an address
  with no lot and no matches; saving, ordering and removing a Homely score; a refused score and a failing save. Every test
  also fails on any uncaught error in the page.
- The module's ordinary `test` task is switched off, so `./gradlew build` does not need a browser; `./gradlew :e2e:e2eTest`
  builds the web app and runs them, and CI runs that as its own job.

## Consequences
- They do not exercise the real server or database (their own tests do) and cannot see how the map looks; screenshots
  and visual checks stay manual.
- A change that renames an element id or a text the tests rely on needs the test updated with it, which is the point.
- Verified to fail: breaking the Homely link address in the app made the suburb card test fail.
