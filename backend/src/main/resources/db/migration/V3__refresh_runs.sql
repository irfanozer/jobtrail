CREATE TABLE refresh_runs (
 id VARCHAR(36) PRIMARY KEY, status VARCHAR(30) NOT NULL,
 started_at TIMESTAMP WITH TIME ZONE NOT NULL, finished_at TIMESTAMP WITH TIME ZONE,
 results_json TEXT NOT NULL
);
CREATE INDEX idx_refresh_started ON refresh_runs(started_at);

