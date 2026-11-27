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
`make install` configures the standard JAVARA profile with the built-in
authentication API, JWT validation, service authentication, email, and
authentication rate limits enabled.
`make setup` instead asks which runtime modules to enable. SMTP and initial
administrator prompts appear only when the built-in authentication API is
enabled; Redis prompts appear only when its rate limits are enabled. The SMTP
password prompt does not echo input, and supplied credentials are written
directly into `config/application.yml`.
`make install` asks only for SMTP host and port and leaves credential
placeholders, which must be supplied through the environment/secret manager or
replaced locally. If storing credentials directly in the file, use
`chmod 600 config/application.yml`. Git ignore is not encryption: do not share
the file or include it in unprotected backups, screenshots, or logs. Gmail
requires an App Password, not the account password. Public registration sends a
verification email, and the account cannot sign in until the address is
confirmed. Existing and bootstrap administrator accounts are already verified.
It also copies illustrative password-policy files into `config/security/`
without overwriting files already there. These small examples let the local
API start and demonstrate both checks; they are not a real denylist or breach
corpus and provide no production coverage. You can edit or replace them, then
restart the API. See the [password-policy setup](production-and-adoption.md#password-policy-datasets)
before using approved datasets or deploying.

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

If PostgreSQL is already available, run:

```bash
make setup
```

The first prompts select the built-in authentication API, JWT resource-server
validation, service-to-service authentication, and authentication rate
limiting. Defaults keep the standard feature set enabled. The setup stops
before writing configuration if an enabled module is missing a required
dependency. Redis details are requested only when authentication rate limiting
is enabled; administrator credentials and SMTP settings are requested only
when the built-in authentication API is enabled. A JWT key and token transport
are requested only when resource-server validation is enabled. Token transport
defaults to `COOKIE` with the authentication API and `BEARER` without it.

The setup also asks for the PostgreSQL connection, application name (default
`JAVARA`), email attribution (default `JAVARA`), and application timezone
(IANA; default `UTC`). Enter `none` to omit the email's “Powered by”
attribution. It checks required service ports when `nc` is installed and writes
the selected modules to `config/application.yml`. Password-policy sample files
are created only when the built-in authentication API is enabled. The
application verifies the database and, when rate limiting is enabled, Redis
connections at startup.

After the initial setup, tune only the area you need:

```bash
make db-setup
```

This configures the PostgreSQL connection and pool in
`config/database.yml`. To terminate HTTPS in the application, run
`make tls-setup`; to require trusted client certificates for inbound HTTPS,
configure TLS first and then run `make mtls-setup`. These commands write
separate ignored configuration files and do not replace the rest of the
runtime settings. When HTTPS terminates at a reverse proxy, application TLS
can remain disabled.

For a CRUD service that should validate compatible JAVARA tokens but not expose
JAVARA's user sign-in, choose `auth-api: no`, `resource-server: yes`,
`service-auth: no`, and `rate-limiting: no`. Choose `resource-server: no` only
when the service should not validate JWTs at all. See the [runtime modules
section](configuration.md#runtime-modules) for exact behavior and dependency
rules.

Configure authentication security separately when ready:

```bash
make security-setup
```

This writes suspicious-authentication detection and response settings to
`config/security.yml`; it does not replace the initial application settings.

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
| `make setup` | Create the initial runtime configuration and select modules |
| `make db-setup` | Configure PostgreSQL connection and HikariCP pool settings |
| `make tls-setup` | Configure application-terminated HTTPS or leave it disabled |
| `make mtls-setup` | Require trusted client certificates for inbound HTTPS |
| `make security-setup` | Configure authentication threat detection and response separately |
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
