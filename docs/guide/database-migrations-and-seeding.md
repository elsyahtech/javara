# Database Migrations and Development Seed Data

This guide explains how to change the database schema and how to add sample
data for local development. JAVARA does not insert a demo user or run example
seed data automatically.

## Migration and seed data are different

- A **migration** changes the database structure, such as creating a table or
  adding a column. Flyway applies application migrations when JAVARA starts.
- **Seed data** is sample or initial data inserted into existing tables.
  Development examples should be run deliberately and must not run as part of
  a production startup.

Keep application migrations and optional examples in separate directories:

| Purpose | Directory | Runs automatically? |
|---|---|---|
| JAVARA and application schema migrations | `src/main/resources/db/migration/` | Yes, at application startup |
| Optional product schema example | `examples/postgres/products/` | No |
| Optional development product data | `examples/postgres/products/seed-dev.sql` | No |
| Optional development authorization data | `examples/postgres/authorization/seed-dev.sql` | No |

Flyway only scans its configured migration directory. It does not scan
`examples/`.

JAVARA reserves the `javara_` table prefix for its own data, including
authentication, authorization, MFA, and tenant tables. Use a distinct
application or domain prefix for client-owned tables so ownership remains
clear. `flyway_schema_history` keeps Flyway's standard name.

The canonical relational design and logical-to-physical type mappings are
documented in the [JAVARA logical database model](../database-logical-model.md).
PostgreSQL migrations are the current physical implementation; other SQL
drivers need vendor-specific DDL while preserving the same model and behavior.

## Add a schema migration

1. Make sure PostgreSQL is available and use a development database, not
   production.
2. Choose the next unused migration number. Check
   `src/main/resources/db/migration/` for the latest version before naming
   your migration.
3. Create a file under `src/main/resources/db/migration/` using this name
   format:

   ```text
   V9__create_products.sql
   ```

   Use two underscores between the version and description. Keep the
   description short and use underscores between words.
4. Write the schema change in that file. For a complete PostgreSQL products
   example, see
   [`V4__create_products.sql`](../../examples/postgres/products/V4__create_products.sql).
5. Start JAVARA with `make run`. Flyway applies the new migration and records
   it in `flyway_schema_history`.
6. Check the startup logs and verify the table in PostgreSQL.

The example file is **not** in Flyway's automatic directory. To try it in a
local JAVARA checkout, copy it into the migration directory and then start the
application:

```bash
test ! -e src/main/resources/db/migration/V9__create_products.sql
cp examples/postgres/products/V4__create_products.sql \
  src/main/resources/db/migration/V9__create_products.sql
make run
```

If you maintain a fork with additional migrations, choose its next unused
number and rename the file. Do not copy a migration over an existing file.

Flyway records each migration after it runs. Do not edit or remove a
migration that has been applied to a database shared with other developers or
used outside local development. Add a new migration to make later changes.
For example, add `V5__add_product_sku.sql` instead of changing the old
products migration.

The current pre-release migration set was updated to create JAVARA-owned
tables with the `javara_` prefix from the beginning. A database carrying
Flyway history from the earlier unprefixed migration files will fail checksum
validation; use a new, empty database for this version rather than baselining
over the old schema.

## Add optional development seed data

The complete example seed is
[`seed-dev.sql`](../../examples/postgres/products/seed-dev.sql). It inserts
two clearly labeled sample products and can be run more than once without
duplicating those IDs.

First, apply the products migration as described above. Then, from the
repository root, run the seed against the local Docker-managed PostgreSQL
service:

```bash
bash scripts/docker-compose.sh exec -T postgres sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < examples/postgres/products/seed-dev.sql
```

The command uses the local database credentials from `.env` inside the
container; it does not put a password on the command line. If you use an
existing PostgreSQL service instead, use `psql` with the connection details
for your development database and run the same file:

```bash
psql "$JAVARA_DEV_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -f examples/postgres/products/seed-dev.sql
```

Set `JAVARA_DEV_DATABASE_URL` in your local shell to the connection URL for a
disposable development database. Do not paste production credentials into
documentation, shell history, or source control.

To confirm the records were inserted, open the local database prompt:

```bash
make psql
```

Then run:

```sql
SELECT id, name, is_active, created_at
FROM products
WHERE deleted_at IS NULL
ORDER BY name;
```

## Try the built-in and sample roles

The role foundation migration seeds two built-in roles and the initial
permission catalog:

- `USER` receives `auth:self`, for authenticated self-service operations.
- `ADMIN` receives all permissions in the initial catalog.

These are system-managed roles; the application prevents them from being
edited or deleted. The initial catalog includes `users:read`, which is
enforced by the user-management API.

For a custom-role example, the optional
[`seed-dev.sql`](../../examples/postgres/authorization/seed-dev.sql) creates
`ACCOUNT_READER` with only the `users:read` permission. It does not create a
user or assign the role to anyone. Run it only against a local development
database after the role foundation migration has been applied:

```bash
bash scripts/docker-compose.sh exec -T postgres sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < examples/postgres/authorization/seed-dev.sql
```

The seed is safe to rerun. To assign the example role, authenticate as an
administrator with `roles:manage` and call
`PUT /api/v1/users/{userId}/roles` with:

```json
{
  "roles": ["ACCOUNT_READER"]
}
```

JAVARA retains the baseline `USER` role when replacing assignments. The sample
permission allows reading user accounts, so assign it only when that access is
intended. To model a client application's own capabilities, add a reviewed
permission to its database catalog and enforce that permission in the
application endpoint; creating a role or permission string alone does not
grant endpoint access.

## Safety rules for seed data

- Keep optional development seeds outside
  `src/main/resources/db/migration/`; otherwise Flyway will run them at
  application startup.
- The authorization sample is not a production default and is not assigned
  automatically. Review its permission before assigning it to any account.
- Never put demo passwords, API keys, personal information, or real customer
  data in a seed file.
- Do not use this sample seed in production. Production data should be
  provisioned through a deliberate, reviewed process.
- Use a separate database for development and tests. The integration tests
  use isolated PostgreSQL containers by default.
- The example uses `ON CONFLICT (id) DO NOTHING` so re-running it does not
  overwrite existing product records.

JAVARA's initial ADMIN is configured separately through
`app.security.bootstrap-admin`; it is not created by a SQL seed.

## If you used an earlier local checkout

The demo-user migrations were removed before release. If a local database
from an earlier checkout already recorded those migration versions, Flyway
may report that an applied migration is missing. Do not point that database
at the updated code. For a disposable local database, `make db-reset` removes
all local PostgreSQL data and requires confirmation; use it only if you are
certain the data can be deleted. Otherwise, keep the old database untouched
and configure JAVARA to use a new, empty development database.
