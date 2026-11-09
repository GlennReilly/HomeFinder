-- The NAPLAN figure is now the school's difference from the national average in points, which can be negative (ADR 0069), not an absolute
-- scale score from 0 to 1000 (ADR 0068). Widen the check to -1000..1000 (the same loose bound as NaplanRules); every row saved under V25 stays valid.
ALTER TABLE school_naplan DROP CONSTRAINT school_naplan_score_check;
ALTER TABLE school_naplan ADD CONSTRAINT school_naplan_score_check CHECK (score BETWEEN -1000 AND 1000);
COMMENT ON COLUMN school_naplan.score IS 'Difference from the national average in NAPLAN points, above (+) or below (-), typed in by the user (ADR 0069)';
