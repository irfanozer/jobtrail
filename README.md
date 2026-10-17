# JobTrail

A reusable job-search workspace built with Java 21, Spring Boot, PostgreSQL, React, and TypeScript. Find real listings on official employer boards, compare them with your career facts, and keep your shortlist and application history in one place.

## What works today

- Live collection from Greenhouse and Lever public employer boards, with editable tracked sources.
- Keyword search, level/workplace/employer filters, pagination, and transparent priority ranking.
- Save, hide, and manually mark jobs applied; history survives refreshes and app restarts.
- A private profile with skills, sourced career facts, and evidence-based match explanations.
- Persisted refresh progress and browser search preferences.
- One-container web application, durable PostgreSQL storage, hosted login, tests and CI.

The initial board list is a starting point, not a search of the whole internet. Add employer board keys to expand coverage. Default ranking favors junior/mid-level engineering roles, remote work, and the Washington/DC region; all locations remain searchable. Unknown seniority and work arrangements stay labeled unknown.

Current matching uses deterministic rules. NVIDIA model integration, tailored resume generation, and reviewed application assistance are later milestones. This version does not submit applications or require an API key.

## Start on Windows

Install Docker Desktop and start its engine. Open PowerShell in the project folder:

```powershell
.\scripts\start.ps1
```

Open **http://127.0.0.1:8080**. The first build downloads dependencies and takes longer. Later runs reuse the build cache. The script creates a private `.env` with randomly generated passwords only when it does not already exist. The app is published on this computer only; local authentication is off by default. To enable it, set `JOBTRAIL_AUTH_ENABLED=true` in `.env` and rerun the script; the username/password are in that private file.

For macOS/Linux, copy `.env.example` to `.env`, supply unique database and owner passwords, then run `docker compose up -d --build --wait`.

## Use it again tomorrow

1. Start Docker and run `scripts/start.ps1` if the app is stopped.
2. Open **Discover jobs** and select **Refresh boards** to fetch current postings.
3. Search and filter; open a listing to read its description and match explanation.
4. Save relevant roles. Follow the employer link to apply, then mark the role **Applied** yourself.
5. Keep **Your profile** current as you gain experience. Add more employers under **Employer boards**.

Sources, profiles, jobs, and statuses live in the `postgres_data` Docker volume. Filters live in your browser. Repeated refreshes update existing jobs; they do not reset your history. An unavailable board retains its previous jobs and displays an error. Closed postings remain accessible in your tracker.

```powershell
docker compose stop                  # Pause without deleting data
docker compose up -d                 # Resume existing containers
docker compose logs --tail=100 app   # Diagnose a startup problem
```

Do not remove the database volume if you want to keep your history. Back up before major upgrades.

## Your private profile

No personal profile or resume is included in this repository. Enter real information in the profile editor. Facts have a source and a confirmation checkbox; unconfirmed claims are not used as evidence.

An optional JSON import can be placed at `private/profile-import.json` and loaded with:

```powershell
.\scripts\import-profile.ps1
```

The import checks the saved version before changing anything. It refuses to overwrite a newer profile automatically. `.env`, `private/`, `data/`, `backups/`, resumes and build outputs are ignored by Git and excluded from container builds. The hosted `deploy` profile requires owner login and an external database.

## Backup and restore

```powershell
.\scripts\backup.ps1
.\scripts\restore.ps1 -BackupPath .\backups\your-backup.dump
```

Backups contain private data; store them securely outside this computer too. A checksum is saved next to each backup. Restore creates a **new recovery database**, leaving the running database intact. To switch the app to the restored copy:

```powershell
.\scripts\restore.ps1 -BackupPath .\backups\your-backup.dump -Activate
```

Activation preserves the previous database and saves a copy of the old `.env`. Backup always targets the database currently configured for the app. These scripts support the local Docker database; use your database provider's export tools for a hosted database.

## Development and verification

Use Java 21+ and Node.js 24. The Maven wrapper is included.

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

In a second terminal:

```powershell
cd frontend
npm ci
npm test
npm run dev
```

Development UI: http://127.0.0.1:5173. Its API proxy targets port 8080. Development uses file-backed H2 under `backend/data/`; Docker uses a separate PostgreSQL database. They do not automatically share profiles. Stop Docker's app before starting another backend on port 8080.

The automated suite covers provider parsing/pagination, failed snapshot rollback, deduplication, concurrent tracking changes, search boundaries, profile version conflicts, matching evidence, real login and CSRF requests, and frontend state. CI also builds the production image, runs migrations against PostgreSQL, and verifies a profile survives an app restart. Live employer APIs are not required by the unit tests.

## Hosting and design

- [Free hosting instructions](docs/deployment.md): Render Docker web service with an external persistent database; Railway considerations.
- [Architecture and tradeoffs](docs/architecture.md)
- [API reference](docs/api.md)
- [Verification record](docs/verification.md)

Run a single application instance. Public source code does not make personal career data public: the hosted app is an owner-only workspace. Use screenshots without profile details when sharing the project.
