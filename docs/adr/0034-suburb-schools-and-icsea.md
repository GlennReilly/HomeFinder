# 0034. Suburb schools and ICSEA

Status: Accepted

## Context
You asked for local school ratings on the suburb details. Both sources were read before deciding:
- **NSW Education's public schools master dataset** (data.nsw.gov.au) is Creative Commons Attribution, CSV or JSON, updated
  nightly (ICSEA yearly), and has each government school's position, kind, enrolments and ICSEA. Small schools and senior
  secondary schools have no published ICSEA; non-government schools are not in it.
- **ACARA's My School data** cannot be copied: its data is released only to named people under a licence or application, and
  its terms restrict comparative school lists. A deep link to one school's My School page cannot be built from the dataset, and
  My School's own search sits behind its terms-of-use page, so no such link is used.
You chose ICSEA with a link to My School, for the public schools inside the suburb.

## Decision
- `download-schools.sh` (run by you) fetches the one CSV; `load-schools.sh` loads it into `school` (migration V15). The staging
  table takes its columns from the file's own header, so a reordered file still loads, and the loader checks the columns it needs by
  name and stops naming the one that is missing if the dataset changes. Rows without a numeric school code or a position are skipped;
  names are trimmed; "np", blank or impossible ICSEA and enrolments become NULL, never zero. Each school gets the pilot suburb
  that contains it, else none.
- The suburb card's **Schools** row reads "3 public schools, average ICSEA 1,032 (1,000 is average)" (the mean of the schools that
  have one) or "No public schools inside the suburb". Under it each school is listed with its kind, ICSEA ("ICSEA not published"
  where it is not) and enrolments, linked to its own website when the dataset has one, and one link to My School's home page says to look the
  schools up by name there.
- The note on the row says what ICSEA is: how socio-educationally advantaged a school's students are, national average 1000, **not how good
  the school is**; and that only NSW public schools are covered and catchments are not included yet. Source:
  "NSW Department of Education public schools master dataset (CC BY); ICSEA from ACARA", as-of date the extraction date, confidence HIGH
  (an official dataset, for the schools it lists).
- A website from the data is only linked if it is an http or https address (checked on the server and again on the card), so a bad value
  can never become a script link.

## Consequences
- Schools in nearby suburbs, private and Catholic schools and catchments are not shown (a later PR could add catchments, also open data).
- The downloaded address can change when the Department republishes the file; `SCHOOLS_URL` is one setting, and the download script
  checks it got the schools file.
- Attribution (CC BY): the card names the Department of Education and ACARA; the README repeats it.
