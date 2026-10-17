ALTER TABLE jobs ADD COLUMN priority_score INTEGER NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN engineering_role BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_jobs_priority ON jobs(engineering_role, active, priority_score DESC, first_seen_at DESC);
