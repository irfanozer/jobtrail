# Verification record

Checked on 2026-10-17 UTC. Live board totals describe a snapshot and will change as employers update their postings.

## Automated checks

- 124 backend tests passed, including source parsing, complete pagination, transactional rollback, duplicate prevention, concurrent status preservation, search, profile conflicts, matching, login and CSRF.
- 5 frontend tests passed; production build and formatting checks passed.
- GitHub Actions also verified the packaged UI and PostgreSQL persistence across an app restart.
- The production Docker image built with Java 21 and ran with a 512 MiB application memory limit.

## Hosted checks

[Hosted JobTrail](https://jobtrail-bwgi.onrender.com) requires owner login.

- `/actuator/health` returned HTTP 200 with status `UP`.
- Unauthenticated profile and jobs API requests returned HTTP 401; the home page redirected to `/login`. Owner sign-in succeeded in the browser.
- All 13 enabled employer boards refreshed successfully, collecting 3,274 postings.
- The default engineering filter returned 803 roles; a combined Python/AWS search returned 176 matches.
- A privately imported profile retained its career facts and sources after a page reload and a fresh sign-in. The role detail view produced rules-based match explanations using the saved profile.
- Saving a role persisted across a page reload and appeared in the application tracker. The temporary test change was restored to its original new status.
- The hosted desktop dashboard was visually inspected; a screenshot without private profile data is included in the README. Hosted service restart and mobile viewport checks are not covered by this record.

## Live local checks

- The application and PostgreSQL containers became healthy using the documented Windows startup script.
- All 13 enabled employer boards completed a real refresh successfully.
- 3,274 postings were stored, with exactly 3,274 distinct source/posting-ID pairs after a repeated refresh.
- The default engineering filter returned 803 roles; a combined Python/AWS search returned 176 matches.
- Job identity, first-seen timestamp, and saved status survived application recreation and a repeated refresh.
- A privately imported profile survived recreation and produced rules-based match explanations.
- The backup script created a custom PostgreSQL dump and checksum. Restoring into a new database reproduced the job count and private-profile payload hash without altering the active database.

Plaid and Sentry had migrated to Ashby. Their obsolete Lever/Greenhouse defaults were removed, and existing matching legacy rows are disabled by migration while preserving any historical data. Ashby is not supported in this release.

The CI fixtures are separate from real profile data. No resumes, personal profile payloads, credentials, backups, or local database files are tracked in Git.
