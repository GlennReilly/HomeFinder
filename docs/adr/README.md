# Architecture decision records

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-gradle-module-layout.md) | Gradle module layout and version pins | Partly superseded by 0019 |
| [0002](0002-postgis-flyway-and-credentials.md) | PostGIS in Docker Compose, Flyway migrations, .env credentials | Accepted |
| [0003](0003-gnaf-pilot-region-and-loading.md) | G-NAF pilot region and loading approach | Accepted |
| [0004](0004-gnaf-completeness-any-overlap-suburbs.md) | Include every overlapping suburb and prove completeness | Accepted |
| [0005](0005-lots-suburbs-crs-and-address-link.md) | Lots, suburbs, projected CRS and the address-to-lot link | Accepted |
| [0006](0006-locality-identity-same-named-suburbs.md) | Identify the right G-NAF locality when suburb names repeat | Accepted |
| [0007](0007-nearest-lot-low-confidence-link.md) | Nearest-lot link as a low-confidence fallback | Accepted |
| [0008](0008-map-library-basemap-and-desktop-finding.md) | Map library, basemap and the desktop finding | Superseded by 0019 |
| [0009](0009-desktop-raster-tile-fallback.md) | Desktop raster-tile fallback map | Superseded by 0019 |
| [0010](0010-desktop-vector-tile-renderer.md) | Desktop vector-tile renderer | Superseded by 0019 |
| [0011](0011-vector-renderer-labels.md) | Street names and other labels on the desktop vector map | Superseded by 0019 |
| [0012](0012-shared-models-and-serialization.md) | Shared models and serialization | Accepted |
| [0013](0013-api-skeleton-auth-and-android-link.md) | API skeleton, API key and the Android link | Partly superseded by 0019 |
| [0014](0014-suburb-vector-tiles.md) | Suburb vector tiles served by the API | Accepted |
| [0015](0015-suburb-layers-on-the-clients.md) | Suburb outlines and names on the clients | Superseded by 0019 |
| [0016](0016-suburb-lookup-and-report-endpoints.md) | Suburb lookup and report endpoints | Accepted |
| [0017](0017-suburb-selection-and-card.md) | Suburb selection and the Suburb Card | Partly superseded by 0057 |
| [0018](0018-sales-statistics-pipeline.md) | Sales statistics pipeline (NSW Valuer General) | Accepted |
| [0019](0019-pivot-to-web.md) | Pivot the target platform to web | Accepted |
| [0020](0020-serve-the-web-app-from-the-api.md) | Serve the web app from the API, local by default | Accepted |
| [0021](0021-suburb-statistics-in-the-report.md) | Suburb statistics in the report | Accepted |
| [0022](0022-lot-tiles-and-lookup.md) | Lot tiles, lot lookup and the basic lot card | Accepted |
| [0023](0023-osm-proximity-pipeline.md) | OpenStreetMap proximity pipeline | Accepted |
| [0024](0024-proximity-in-the-lot-report.md) | Proximity in the lot report | Accepted |
| [0025](0025-blank-postcode-sales-match-by-name.md) | Blank-postcode sales match by name in the pilot councils | Accepted |
| [0026](0026-address-and-suburb-search.md) | Address and suburb search | Accepted |
| [0027](0027-link-out-to-homely-no-extraction.md) | Link out to Homely; do not extract its ratings | Accepted |
| [0028](0028-manual-homely-rating.md) | A Homely star score you enter by hand | Accepted |
| [0029](0029-rated-suburb-labels-follow-the-view.md) | Rated suburb labels follow the view | Accepted |
| [0030](0030-homely-rating-under-price-change.md) | The Homely score sits under the price change | Superseded by 0043 |
| [0031](0031-rated-label-after-two-zoom-taps.md) | The score label waits until you have zoomed in two taps | Accepted |
| [0032](0032-browser-smoke-tests.md) | Browser smoke tests | Accepted |
| [0033](0033-suburb-osm-rollup.md) | Suburb roll-up of the OpenStreetMap features | Accepted |
| [0034](0034-suburb-schools-and-icsea.md) | Suburb schools and ICSEA | Accepted |
| [0035](0035-suburb-filter-panel.md) | Suburb filter side panel | Accepted |
| [0036](0036-lot-height-and-slope-from-dem.md) | Lot height and slope from the 5 m DEM | Accepted |
| [0037](0037-dem-request-in-degrees.md) | DEM request in degrees, resampled to metres | Accepted |
| [0038](0038-sales-window-ends-on-latest-sale.md) | Sales windows end on the latest sale | Accepted |
| [0039](0039-card-rows-hidden-and-reordered-in-the-browser.md) | Card rows are hidden and re-ordered in the browser | Partly superseded by 0060 |
| [0040](0040-height-above-drainage-hint.md) | Height above drainage as an indicative hint | Partly superseded by 0054 |
| [0041](0041-cards-show-only-rows-with-a-reliable-source.md) | Cards show only rows with a reliable source | Accepted |
| [0042](0042-empty-dem-tiles-are-no-data.md) | Empty DEM tiles are no data | Accepted |
| [0043](0043-homely-block-with-hidden-edit-form.md) | The Homely block holds the rating, its reviews link and a hidden edit form | Partly superseded by 0060 |
| [0044](0044-census-tenure-and-dwelling-structure.md) | Share rented and the mix of houses and units from the 2021 Census | Accepted |
| [0045](0045-lot-card-schools-nearest-by-straight-line.md) | The Lot Card shows the nearest public primary and high school | Partly superseded by 0056 |
| [0046](0046-filters-beach-parks-rented.md) | Filters on beach distance, park share and homes rented | Partly superseded by 0052 |
| [0047](0047-weighted-score-mode.md) | Weighted-score mode for suburbs | Superseded by 0048 |
| [0048](0048-right-hand-tabs.md) | Suburb and Filters tabs in the right-hand panel | Accepted |
| [0049](0049-seifa-socio-economic-advantage.md) | Socio-economic advantage from SEIFA 2021 | Accepted |
| [0050](0050-other-seifa-indexes.md) | The other three SEIFA indexes on the Suburb Card | Accepted |
| [0051](0051-bocsar-crime-rates.md) | Recorded crime per 1,000 residents from BOCSAR | Accepted |
| [0052](0052-single-slider-filters.md) | One slider with a fixed direction for each filter | Accepted |
| [0053](0053-pinned-card-header-reset-all-kept-scroll.md) | Pinned card header, Reset all, and a card that keeps its scroll position | Accepted |
| [0054](0054-hand-not-suitable-for-flat-areas.md) | Height above drainage is not offered in flat suburbs | Accepted |
| [0055](0055-capped-crime-sliders.md) | Capped crime sliders with a "No limit" position | Accepted |
| [0056](0056-school-catchments.md) | The Lot Card shows the school catchments the lot lies in | Accepted |
| [0057](0057-select-suburb-without-dimming-the-rest.md) | Selecting a suburb changes only that suburb | Accepted |
| [0058](0058-hide-unknown-card-rows.md) | Rows without data are left off the cards | Accepted |
| [0059](0059-sales-count-in-price-change.md) | The sales count sits in the price change row | Accepted |
| [0060](0060-homely-block-at-the-bottom.md) | The Homely block is the last section of the Suburb Card | Accepted |
| [0061](0061-listing-links.md) | Links to listings on realestate.com.au and Domain | Accepted |
| [0062](0062-suburb-catchments-and-zoned-icsea-filter.md) | School catchments on the Suburb Card and an ICSEA filter on them | Accepted |
| [0063](0063-sources-collapsed-by-default.md) | Source lines are collapsed by default | Superseded by 0072 |
| [0064](0064-card-row-groups.md) | Card rows are grouped into folding sections | Accepted |
| [0065](0065-filter-shading-fades-at-lot-zoom.md) | The suburb filter shading fades out at lot zoom | Accepted |
| [0066](0066-filter-groups.md) | Filters are grouped into folding sections | Accepted |
| [0067](0067-narrow-bottom-sheet-layout.md) | Narrow windows: half-height sheet, compact lot header, fold chevron | Accepted |
| [0068](0068-manual-naplan-score-per-school.md) | A NAPLAN score per school, typed in by hand from My School | Partly superseded by 0069 |
| [0069](0069-naplan-difference-from-national-average.md) | The NAPLAN figure is a signed difference from the national average | Partly superseded by 0071 |
| [0070](0070-schoolrank-link-out.md) | A SchoolRank link beside each school (link out only) | Accepted |
| [0071](0071-naplan-year-optional.md) | The NAPLAN year is optional and the note is removed | Accepted |
| [0072](0072-one-details-fold-per-row.md) | One closed Details section per row holds the note and the source | Accepted |
