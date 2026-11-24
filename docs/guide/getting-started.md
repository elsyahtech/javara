# Getting Started

This page takes you from a clean checkout to a working local API. The commands
below do not require an existing PostgreSQL or Redis installation.

## Requirements

- JDK 25
- Docker and Docker Compose for the default local setup
- A terminal with `make`

`make install` can offer to install JDK 25 and Docker/Compose. On macOS,
Homebrew must already be installed. Linux installation is best-effort; the
installer prints manual instructions when it cannot install a dependency.

## Option A: Let JAVARA create local services

From the repository root, run:

```bash
make install
```

The installer asks before installing system packages, creates local-only
PostgreSQL and Redis credentials, and starts the database containers. It does
not start the API.

Then start JAVARA:

```bash
make run
```

The setup writes runtime settings to `config/application.yml`. That file is
local, has restricted file permissions, and is ignored by Git. The generated
configuration binds the API to `127.0.0.1:8080`.

Check that the API is responding:

```bash
curl http://localhost:8080/
```

The response should contain `Welcome`.

The database and Redis data are kept in Docker-managed storage. To stop the
services without removing their data:

```bash
make db-down
```

`make db-reset` permanently deletes the local database data and requires
confirmation. Do not run it if you need to keep local records.

## Option B: Connect to services you already have

If PostgreSQL and Redis are already available, run:

```bash
make setup
```

The setup asks for the connection details, initial ADMIN account, token
transport, and application timezone (IANA; default `UTC`). It generates a JWT
signing key, checks whether the service ports are reachable when `nc` is
installed, and writes `config/application.yml`.
The application verifies the actual database and Redis credentials when it
starts.

Start the API after setup:

```bash
make run
```

Use a dedicated database for JAVARA. Flyway applies the migrations at startup;
do not point a development checkout at a production database.

## The first administrator

On the first successful startup, JAVARA creates the configured ADMIN account
if its email is unused. It does not promote an existing USER account. If the
email already belongs to a USER, startup stops with an error so that access
is not changed silently.

After confirming that the ADMIN account can sign in:

1. Remove the bootstrap password from `config/application.yml`.
2. Set `app.security.bootstrap-admin.enabled` to `false`.
3. Keep the signing key and service credentials out of Git and logs.

JAVARA does not seed a demo account. Register a USER or use the initial ADMIN
credentials from your local configuration.

## Run tests

```bash
make test
```

Integration tests use temporary PostgreSQL 16 and Redis 7 Testcontainers by
default and need Docker to be running. They do not use the application's local
database. Dedicated test services can be configured with the `JAVARA_TEST_*`
environment variables described in the root [README](../../README.md#testing).
Never point tests at a database containing data you need.

## Useful commands

| Command | What it does |
|---|---|
| `make install` | Install/check local prerequisites and start local services |
| `make setup` | Configure connections to existing PostgreSQL and Redis services |
| `make run` | Start the Spring Boot API |
| `make test` | Run unit and integration tests |
| `make build` | Build the application package |
| `make db-up` | Start the local Docker Compose services |
| `make db-down` | Stop services and keep their data |
| `make db-reset` | Delete local database data after explicit confirmation |
| `make backup-setup` | Configure optional scheduled database backups and expired-token cleanup |
| `make backup-run` | Run one backup and cleanup immediately |
| `make backup-disable` | Disable JAVARA's managed cron job without deleting backup files |

## Optional database backups

Backups are an infrastructure task outside the JAVARA runtime. Run
`make backup-setup` and answer the prompts to choose the PostgreSQL connection,
dump format (`custom` or `plain`), destination (encrypted filesystem, S3, or
both), schedule, backup retention (default 30 days), and expired-token grace
period (default 7 days). For S3, configure AWS credentials separately using an
AWS profile or the standard AWS credential chain. Enter the same database used
by the running JAVARA instance:
cleanup runs against the database selected here. The setup checks the database
schema and S3 bucket access before installing the scheduler; it does not create
or verify a full backup during setup. Connection settings are stored in the
Git-ignored `config/backup.env` with file mode `600`.

The filesystem destination must be an absolute path outside the repository on
encrypted storage. For S3, the bucket must already exist and the credentials
must allow the required bucket check and backup operations. The optional
endpoint field supports S3-compatible services; non-local endpoints must use
HTTPS. Choose AES256 encryption or confirm that the bucket enforces encryption.

The setup checks for PostgreSQL client tools (`pg_dump`, `psql`, and
`pg_restore` for custom-format dumps) and offers to install them if missing.
On macOS it uses Homebrew `libpq`; on supported Linux distributions it uses
the system package manager and may ask for administrator permission. For S3
destinations it similarly offers to install AWS CLI. If `crontab` is missing
on supported Linux distributions and cron scheduling is selected, setup offers
to install the cron package; make sure the system cron service is running.
`pg_dump` must be the same major version as, or newer than, the PostgreSQL
server. If automatic installation is unavailable for your OS, install the
tools manually and rerun the setup.

The default scheduler is the current operating system user's cron. Its default
schedule is daily at 02:00 in the host's timezone, not the application's
configured timezone. The machine must be running at the scheduled time; on
Linux, ensure the cron service is active. Setup installs the schedule only
after validation and does not run a backup immediately. Keep the repository at
the same path while the cron entry is installed. Scheduled output is appended
to `config/backup.log`; inspect the installed entry with `crontab -l`.

Choose `external` if your deployment manages jobs elsewhere, then register
this command with that scheduler:

```bash
/bin/bash /path/to/javara/scripts/backup-run.sh --scheduled
```

Use `make backup-run` to run the same operation immediately; its output is
printed to the terminal. The job creates a full `pg_dump` of the selected
database, verifies the dump and every selected destination, then prunes backup
files older than the configured backup retention. Only after successful
verification does it delete expired refresh tokens that are past the configured
grace period. A failed dump or publish skips cleanup. Cleanup is batched to
avoid holding locks on the whole table. Start with a manual run and check its
result before relying on the schedule.

The backup is a `pg_dump` of the JAVARA database, not PostgreSQL cluster-wide
roles or server settings. Protect database dumps as sensitive data and test
restores regularly. To verify a local backup checksum, run the matching command
from the backup directory:

```bash
shasum -a 256 -c "$BACKUP_FILE.sha256" # macOS
sha256sum -c "$BACKUP_FILE.sha256"    # Linux
```

Set `BACKUP_FILE` to the dump filename first. Restore into a new, empty
database rather than overwriting production data. If the backup is in S3,
download both the dump and its checksum file first. Configure the normal
PostgreSQL connection environment (`PGHOST`, `PGPORT`, `PGUSER`) for the target
server before restoring; PostgreSQL can prompt for a password or use a
protected `.pgpass` file. For a custom dump:

```bash
createdb javara_restore
pg_restore --dbname=javara_restore --no-owner --no-privileges "$BACKUP_FILE"
```

For a plain SQL dump:

```bash
createdb javara_restore
psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --dbname=javara_restore --file="$BACKUP_FILE"
```

The custom-dump example omits ownership and grants for a portable test restore.
The plain SQL dump can contain ownership and grant statements, so the referenced
roles must exist before restoring it. For full recovery, recreate the required
PostgreSQL roles and other cluster-level settings separately; a
single-database `pg_dump` does not contain them.
`make backup-disable` removes only the cron job managed by JAVARA; it leaves
the config and existing backups in place. Disable externally managed jobs in
their own scheduler.

If startup fails, check the message first. A missing signing key or stale local
configuration can usually be fixed by reviewing `config/application.yml` or
running `make setup` again. See the [README troubleshooting section](../../README.md#troubleshooting)
for database and migration problems.
