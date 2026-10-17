# API reference

Local base URL: `http://localhost:8080`. In hosted mode, sign in at `/login`; mutations require the `XSRF-TOKEN` cookie copied to the `X-XSRF-TOKEN` request header. JSON requests use `Content-Type: application/json`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/actuator/health` | Public health check |
| GET | `/api/session` | `authenticationEnabled`, `demo`, and `demoAvailable` flags |
| POST | `/demo` | CSRF-protected entry into a fresh temporary demo session; redirects to `/` |
| GET | `/api/jobs` | Filtered, paginated listings |
| GET | `/api/jobs/{id}` | Full posting |
| PATCH | `/api/jobs/{id}/status` | `{ "status": "SAVED" }` |
| GET | `/api/jobs/{id}/match` | Evidence-based match explanation |
| GET | `/api/sources` | Employer boards and refresh health |
| POST | `/api/sources` | `{ "name": "Employer", "provider": "GREENHOUSE", "boardKey": "employer" }` |
| PATCH | `/api/sources/{id}` | `{ "enabled": false }` |
| POST | `/api/refresh` | Start asynchronous collection (202) |
| GET | `/api/refresh/latest` | Most recent run, or 204 if none |
| GET | `/api/refresh/{id}` | Run state and individual board outcomes |
| GET | `/api/profile` | Private profile and version |
| PUT | `/api/profile` | Replace profile, passing its current version |

Job query parameters: `q` (up to 200 characters), `level` (`JUNIOR`, `MID`, `SENIOR`, `UNKNOWN`), `workplace` (`REMOTE`, `HYBRID`, `ONSITE`, `UNKNOWN`), `status` (`NEW`, `SAVED`, `APPLIED`, `HIDDEN`), `sourceId`, `engineeringOnly` (default true), `includeInactive` (default false), `sort` (`priority` or `newest`), `page` (zero-based), and `size` (1–100).

The job response contains `items`, `totalElements`, `totalPages`, `page`, and `size`. Hidden jobs are excluded unless explicitly requested. Inactive saved/applied jobs are available with `includeInactive=true`.

Profiles contain `displayName`, `preferredLocations`, `skills`, `targetRoles`, `careerFacts`, and `version`. Each fact contains `id`, `statement`, `source`, and `confirmed`. A new profile has version 0. A successful save increments the version; stale updates return 409. Confirmed facts require a nonblank source. Keep imports under the ignored `private/` directory.

Demo sessions use the same job, profile, and matching endpoints with a fictional profile and isolated tracking overlay. Job status filters, counts, and pagination use only that visitor's statuses. Profile and tracking edits are temporary, capped at 500 tracked jobs per session, and reset on logout, 30 minutes of inactivity, or service restart. All source mutations and refresh endpoints are owner-only. Unknown API paths also require the owner role. Demo mutations and logout require CSRF protection just like owner requests.

Refreshes report `RUNNING`, `COMPLETED`, `PARTIAL`, or `FAILED`. The response retains individual board errors without exposing raw server exceptions. A failed board is not proof that an employer has no jobs. Repeated refresh requests return the current run while one is active; starting again within 30 seconds returns 429.
