-- The year of a NAPLAN figure is optional and the note is gone from the feature (ADR 0071). The year: drop NOT NULL, and restate the check so it plainly allows a missing year while a given year
-- must still be from 2008. Rows saved under V25 and V26 keep their year.
ALTER TABLE school_naplan ALTER COLUMN year DROP NOT NULL;
ALTER TABLE school_naplan DROP CONSTRAINT school_naplan_year_check;
ALTER TABLE school_naplan ADD CONSTRAINT school_naplan_year_check CHECK (year IS NULL OR year BETWEEN 2008 AND 2100);
COMMENT ON COLUMN school_naplan.year IS 'The year the figure is from, if the user gave one (ADR 0071)';
-- The note column is kept (it is already nullable) so notes typed before are not destroyed; the app no longer reads or writes it.
COMMENT ON COLUMN school_naplan.note IS 'Unused since ADR 0071: the app no longer reads or writes it; kept so earlier notes are not lost';
