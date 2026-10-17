-- These employers migrated to a provider not supported by this release.
-- Keep their rows and any historical jobs, but stop requesting obsolete endpoints.
UPDATE job_sources SET enabled = FALSE
WHERE (name = 'Plaid' AND provider = 'LEVER' AND board_key = 'plaid')
   OR (name = 'Sentry' AND provider = 'GREENHOUSE' AND board_key = 'sentry');
