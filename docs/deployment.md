# Free deployment

The repository includes `Dockerfile` and `render.yaml`. Deployment requires your own hosting and database accounts. No infrastructure is provisioned by cloning or running this project.

## Render + Neon

Use a **free Render web service** for the Docker image and a **free Neon PostgreSQL project** for persistent data. The Render filesystem is disposable; all durable application state belongs in PostgreSQL. Do not select Render's expiring free PostgreSQL database for long-term application history.

1. Create a free Neon project in a nearby region. Record the host, database name, role and password privately. Use a pooled endpoint if offered.
2. In Render, create a Blueprint from your fork/repository. Review the proposed service: `jobtrail`, Docker runtime, **Free** plan, one instance. The blueprint does not create a paid database. Use the free workspace without adding a payment method; on an existing billed workspace, review usage billing and set spending limits before continuing.
3. Supply these private environment values:

   | Variable | Value |
   | --- | --- |
   | `JDBC_DATABASE_URL` | `jdbc:postgresql://YOUR_HOST/YOUR_DATABASE?sslmode=require` |
   | `DATABASE_USERNAME` | Your database role |
   | `DATABASE_PASSWORD` | Your database password |
   | `JOBTRAIL_PASSWORD` | A unique password with at least 16 characters |

   Replace `YOUR_HOST` and `YOUR_DATABASE` with the actual values from Neon's Connect dialog. Keep database credentials in their separate variables; do not paste a `psql` command or a URL containing a password into `JDBC_DATABASE_URL`. An `UnknownHostException` naming a placeholder means that replacement was missed.

4. Keep `SPRING_PROFILES_ACTIVE=deploy`, `JOBTRAIL_USERNAME=owner`, and the default scheduled-refresh settings. Never use the local profile for a public deployment.
5. Wait for the container build and `/actuator/health` to become healthy. Open the service URL, sign in, and enter your profile privately. The database starts empty except for the tracked employer boards.
6. Refresh boards and verify results. Restart/redeploy once and confirm the profile and tracking state remain available. Check database storage and compute usage periodically.

The deploy profile requires authentication even if `JOBTRAIL_AUTH_ENABLED=false` is accidentally provided. It also requires explicit external database settings, so a deployment cannot silently store its data in a temporary local file. TLS session cookies require the host's HTTPS URL.

Free Render web services sleep after inactivity and can take time to wake. Scheduled tasks cannot be relied on while the service sleeps. Manual refresh after opening the app is the dependable free-tier workflow. The JVM is configured for a small instance, but very large board sets can exceed free memory/storage allowances. Start with the included sources and expand gradually.

Free-tier conditions can change. Review the current [Render free-service limits](https://render.com/docs/free) and [Neon pricing](https://neon.com/pricing) before creating resources. No uptime or unlimited usage is promised.

## Railway

The same Docker image can run on Railway with `SPRING_PROFILES_ACTIVE=deploy` and the environment variables above. Use an external persistent database and set the service health path to `/actuator/health`.

Railway's trial and Free plan provide limited credits. They do not guarantee enough allowance for an always-on Java service plus a database. Review [Railway trial and Free plan limits](https://docs.railway.com/pricing/free-trial) and keep a hard spending limit before deploying. Render + Neon is the documented starting point when avoiding an automatically billed always-on service is the priority.

## Move existing data

Local backup/restore scripts are for local Docker recovery. To move the full workspace to a hosted PostgreSQL database, make a custom-format backup, restore it into an empty compatible destination using `pg_restore --no-owner --no-privileges`, and verify the destination before switching the hosted app to it. Keep credentials in private environment settings. Never commit a backup or connection URL containing a password.

Alternatively, start with a fresh hosted database and enter the profile in the authenticated editor. Local and hosted databases are independent until explicitly migrated.
