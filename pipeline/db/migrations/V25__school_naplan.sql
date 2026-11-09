-- One overall NAPLAN figure per school, typed in by hand from My School (ADR 0068) for personal, non-commercial use; nothing is fetched.
-- There is no foreign key to `school` on purpose: reloading the schools (load-schools.sh) must never delete what you typed in. School codes
-- are the Department of Education's, which are stable. The limits match NaplanRules in the shared module.
CREATE TABLE school_naplan (
    school_code INTEGER PRIMARY KEY,
    score       SMALLINT NOT NULL CHECK (score BETWEEN 0 AND 1000),
    year        SMALLINT NOT NULL CHECK (year BETWEEN 2008 AND 2100),
    note        TEXT CHECK (char_length(note) <= 200),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
