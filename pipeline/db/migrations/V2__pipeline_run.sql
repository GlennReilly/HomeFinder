-- Records each pipeline stage run and the input versions it used, so reruns are reproducible.
CREATE TABLE pipeline_run (
    id             BIGSERIAL PRIMARY KEY,
    stage          TEXT        NOT NULL,
    input_versions JSONB       NOT NULL DEFAULT '{}'::jsonb,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ
);

CREATE INDEX pipeline_run_stage_idx ON pipeline_run (stage, started_at DESC);
