# 0056. The Lot Card shows the school catchments a lot lies in

Status: Accepted

## Context
ADR 0045 filled the Lot Card's Schools row with the nearest public primary and high school by straight line, because no catchment data was loaded. The nearest
school is not always the one a lot is zoned for. The NSW Department of Education publishes the current public school catchments (intake zones) as map data.

## Decision
- **Source:** "School intake zones (catchment areas) for NSW government schools", NSW Department of Education, on [Data NSW](https://data.nsw.gov.au/data/dataset/8b1e8161-7252-43d9-81ed-6311569cb1d7).
  The user downloads it with `pipeline/scripts/download-catchments.sh` (the sandbox cannot). The download holds three shapefiles (GDA94, EPSG:4283): current primary zones (1,657),
  current secondary zones (443) and future zones (104), and `catchment_sf_info.json` with the current enrolment year (2026). The licence is stated on the listings as Creative Commons
  Attribution 3.0 Australia, credited to the Department; that wording was not read on the dataset page itself, so it is unverified. The Department takes no responsibility if the zones are relied on,
  because enrolment areas change.
- **Current zones only.** The file of future catchments is ignored (the user's decision); a "changing from 2027" note is a possible later step.
- **Loading:** a pure-Kotlin shapefile and dBase reader in the `census` module (no GDAL here) writes a CSV with each zone's shape as WKT (`:census:catchments`); `pipeline/scripts/load-catchments.sh`
  loads it into `school_catchment` (migration V23), reprojecting from GDA94 to GDA2020 (EPSG:7844, the lots' system) with `ST_Transform` and repairing shapes with `ST_MakeValid`. The shapefile
  does not say which ring is a hole: outer rings are the clockwise ones, and each hole goes into the smallest outer ring that contains it. `USE_ID` is the school code and matches `school.school_code`
  (ADR 0034): 2,031 of the 2,036 schools with a zone are in the schools data; the rest show the Department's short name.
- **Lot Card, Schools row:** the server finds the zones that cover the lot's representative point (`ST_PointOnSurface`, as for the schools query). The row reads
  "Zoned: Hamilton Public School (primary, K-6), ICSEA 1,051, Newcastle High School (high, 7-12)": primary first, then high, each with the kind and years of its zone (infants, central school,
  boys or girls high, and a high school split into campuses by years all appear as the Department draws them). **A nearest school is kept only for a level the lot has no zone for**, and is labelled
  "Nearest, not zoned:" (ADR 0045's selection). With no catchments loaded the row is exactly as in ADR 0045. Confidence is medium; the note carries the reliance warning ("being in a zone does not
  guarantee a place, and out-of-area enrolment is common: check with the school"), the enrolment year and the attribution "Based on NSW Department of Education data".
- The response gains `SchoolInfo.zoned` and `zonesChecked` (defaults keep older reports decoding). The Suburb Card and the ICSEA filter are unchanged.

## Consequences
- A lot can show several zones for one level (an infants zone inside a primary zone, a central school's primary and high zones, a high school split by years); all are listed.
- Catchments are today's boundaries only; they change, and the data is as fresh as the user's last download and load.
- Selective, specialist, private and Catholic schools have no zones in this data.
- A lot near a zone boundary can be placed on either side by the representative point; the zone is for the lot's point, not the whole block.
- Possible later steps: the schools whose zones cover most of a suburb on the Suburb Card and in the ICSEA filter; future zones.
