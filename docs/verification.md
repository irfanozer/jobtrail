# Verification record

Checked on 2026-10-17 UTC. Live board totals describe a snapshot and will change as employers update their postings.

## Automated checks

- 129 backend tests passed, including source parsing, complete pagination, transactional rollback, duplicate prevention, concurrent status preservation, search, profile conflicts, matching, login and CSRF.
- Five demo integration tests verify separate visitor sessions, owner-data isolation, status filtering and pagination, rejected owner-only actions, profile conflicts, real cookie/header CSRF requests, and reset after sign-out.
- 6 frontend tests passed; production build and formatting checks passed, including isolation from the owner's saved browser preferences.
- GitHub Actions also verified the packaged UI and PostgreSQL persistence across an app restart.
- The production Docker image built with Java 21 and ran with a 512 MiB application memory limit.

## Hosted checks

The initial [hosted JobTrail](https://jobtrail-bwgi.onrender.com) checks below used the private owner workspace.

- `/actuator/health` returned HTTP 200 with status `UP`.
- Unauthenticated profile and jobs API requests returned HTTP 401; the home page redirected to `/login`. Owner sign-in succeeded in the browser.
- All 13 enabled employer boards refreshed successfully, collecting 3,274 postings.
- The default engineering filter returned 803 roles; a combined Python/AWS search returned 176 matches.
- A privately imported profile retained its career facts and sources after a page reload and a fresh sign-in. The role detail view produced rules-based match explanations using the saved profile.
- Saving a role persisted across a page reload and appeared in the application tracker. The temporary test change was restored to its original new status.
- The hosted desktop dashboard was visually inspected; a screenshot without private profile data is included in the README. Hosted service restart and mobile viewport checks are not covered by this record.

### Temporary public demo

- The deployed login page opened a demo without credentials and displayed its temporary-session notice.
- The shared catalog returned 803 engineering roles from 13 employer boards. The profile was the fictional Alex Morgan example.
- Saving a real listing showed it in the demo tracker and survived a page reload. Its match view used the demo's Java/Python skills and labeled matching as rules-based with AI disabled.
- Signing out and starting a new demo restored an empty tracker. Owner isolation and separate visitor sessions are additionally covered by the integration tests above.

## Live local checks

- A separate Java 21 production container ran the new login and demo UI with a 512 MiB memory limit and disposable in-memory data. In the browser, a fictional profile edit survived reload, sign-out returned to login, and a new demo restored the original fictional profile. This check did not use the owner's local database.
- The redesigned login was visually checked on desktop and at a 390-pixel mobile viewport. Both entry paths remained visible, the panels stacked in DOM order, and the mobile page had no horizontal overflow. Owner sign-in also succeeded against the disposable container.
- The application and PostgreSQL containers became healthy using the documented Windows startup script.
- All 13 enabled employer boards completed a real refresh successfully.
- 3,274 postings were stored, with exactly 3,274 distinct source/posting-ID pairs after a repeated refresh.
- The default engineering filter returned 803 roles; a combined Python/AWS search returned 176 matches.
- Job identity, first-seen timestamp, and saved status survived application recreation and a repeated refresh.
- A privately imported profile survived recreation and produced rules-based match explanations.
- The backup script created a custom PostgreSQL dump and checksum. Restoring into a new database reproduced the job count and private-profile payload hash without altering the active database.

Plaid and Sentry had migrated to Ashby. Their obsolete Lever/Greenhouse defaults were removed, and existing matching legacy rows are disabled by migration while preserving any historical data. Ashby is not supported in this release.

The CI fixtures are separate from real profile data. No resumes, personal profile payloads, credentials, backups, or local database files are tracked in Git.
