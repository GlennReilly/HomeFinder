# 0080. A PDF export of the suburbs the filters shade, made on the server

Status: Accepted

## Context
You asked for "a pdf export option of the details of all suburbs that are shaded by selected filters", and chose that the PDF is made on the server and
downloaded as a real `.pdf` file, with each matching suburb's full Suburb Card details and the active filters listed at the top.

The page already knows which suburbs pass the switched-on filters (the same set the map tints, ADR 0035, 0052), the words each filter shows ("At most 45%",
"No limit"), and your Suburb Card layout (kept in this browser, ADR 0039, 0064). The server has every suburb's report (the suburb report route) but not the
layout. The card's rows are built by pure Kotlin in the `client` module (`SuburbCardRows`, `CardGroups`, `CardLayout`), which has a JVM target.

## Decision
- **Button**: the Filters tab gets **Export PDF (N suburbs)** under the match count, off until at least one filter is on and 1 to
  `ApiContract.MAX_EXPORT_SUBURBS` (200) suburbs match. A hint line under it says what it does or why it is off; a `role="status"` line reports progress,
  the downloaded file name or the error. It uses the small-button look with the 44 px touch area (ADR 0077).
- **Request**: `POST /v1/export/suburbs.pdf` behind the API key, body `SuburbExportRequest { suburbIds, filters, cardLayout }`: the matching ids sorted by
  suburb name, one readable line per switched-on filter ("Homes rented: At most 45%"; the NAPLAN line adds "(your own figures, typed in)"), and the card
  layout as `CardLayout.encode()` writes it. The client sends it through a new `HttpBytes` request (POST, `HttpVerb.POST`) that returns raw bytes and allows
  120 s, since a full export reads up to 200 reports.
- **Server**: 400 `invalid_export` for a body that is not a request or names no suburb, 400 `too_many_suburbs` over the cap, 503 without a database or when
  a lookup fails, 404 when none of the suburbs is found (suburbs not found are left out and counted on the cover). Each report is loaded exactly as the
  suburb report route loads it (one shared `loadSuburbReport`, with your Homely score and NAPLAN figures). The answer is `application/pdf`,
  `Content-Disposition: attachment; filename=homefinder-suburbs-YYYY-MM-DD.pdf`, `Cache-Control: no-store`.
- **Same rows as the card**: the server now depends on the `client` module's JVM build and reuses `SuburbCardRows`, `CardGroups`, `CardLayout` and the
  card's provenance line, rather than moving them to `shared`: no code moves, and the PDF cannot drift from the card. Every row is listed (a row with no data says "No data"), under
  the card group titles, in your layout's group and row order; rows you hid with Customise are **left out**, as on the card. Each row gets its note (or, for a row with no data, the reason) and its whole source line (source, as-of date, confidence).
  The Schools row lists each zoned school with its share, ICSEA and your NAPLAN figure. A Homely score you entered
  closes the suburb as a "Homely" group.
- **PDF library**: Apache PDFBox 3.0.5 (Apache-2.0, pure Java, in the version catalog). It writes text with the standard Type 1 Helvetica, so no font file
  is embedded and nothing is fetched. Helvetica has only the WinAnsi characters, so `PdfText` replaces the rest: stars and diamonds become "*", "≥" ">=",
  control characters are dropped, and anything else the font cannot draw becomes "?"; drawing never throws.
- **Layout**: A4, a cover with the title, date, number of suburbs, filters used, the suburb names and the card's disclaimer; then the first suburb on the same page (no blank half page) and each other suburb from a new
  page, rows in a label column and a value column, text wrapped, a row kept on one page where it fits, a group title never left alone at a page foot, and a
  footer with the date and page number.

## Consequences
- The server module now pulls in `client` (and through it the Ktor client and kotlinx-html JVM jars) and PDFBox (about 3.8 MB with its fontbox, pdfbox-io and
  commons-logging dependencies).
- A full 200-suburb export reads about nine queries per suburb; it may take several seconds, which is why the client allows 120 s. A cap of 200 keeps a
  mistaken export small; widen it in `ApiContract.MAX_EXPORT_SUBURBS` if needed.
- The PDF holds your own figures (Homely, NAPLAN) and is never cached. It is for your own, non-commercial use, like the Valuer General sales behind the
  prices (ADR 0018).
- Characters outside WinAnsi (for example a suburb or school name in another script) print as "?". An embedded Unicode font would fix that at the cost of a
  font file in the repository.
- Rows hidden on the card are left out of the PDF (your choice, 9 Nov 2026); rows with no data are listed as "No data" with their reason.
- The file name uses the browser's date; the server's `Content-Disposition` uses the server's date. They differ only across midnight.
