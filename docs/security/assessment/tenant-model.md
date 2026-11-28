# Tenant and Role Security Model

## Decision and runtime status

- The setup choice is `SINGLE` (one organization) or `SHARED` (multiple
  organizations in one deployment); `SINGLE` is the default.
- `SINGLE` is the only supported runtime mode.
- `SHARED` is deliberately rejected during startup. The schema foundation does
  not provide end-to-end tenant isolation.
- User roles and permission authorities are stored in PostgreSQL. Redis caches
  the effective role/permission snapshot, while PostgreSQL authorization
  revisions ensure changes take effect on the next request. JWT role claims are
  not an authorization source.

## Single-organization role model

Migration
[`V8`](../../../src/main/resources/db/migration/V8__create_tenant_and_role_foundation.sql)
adds the foundation:

- `javara_tenants` and `javara_tenant_memberships` associate users with an organization. In
  `SINGLE`, existing and new users are attached to the internal `default`
  organization.
- `javara_user_roles` stores built-in and custom role definitions.
- `javara_permissions` is the allow-list of capabilities that JAVARA endpoints can
  enforce. `javara_user_role_details` links roles to those permissions.
- `javara_user_role_assignments` links roles to a user's organization membership.
- Existing `USER` and `ADMIN` accounts are backfilled. The legacy `javara_users.role`
  value is retained for compatibility, but authorization uses role assignments
  and permission details.

The built-in role definitions cannot be modified or deleted. `USER` remains a
baseline assignment; user role updates add it if it is omitted. Custom roles
may use only permissions in the catalog. Add new application capabilities
through reviewed code and database changes; arbitrary permission strings do
not grant access.

Migration V8 seeds `USER` with `auth:self` and `ADMIN` with the initial
permission catalog. An optional development-only
[`ACCOUNT_READER` seed](../../../examples/postgres/authorization/seed-dev.sql)
demonstrates a custom role with only `users:read`; it creates no user and
assigns the role to nobody. The [database seeding guide](../../guide/database-migrations-and-seeding.md)
shows how to run it and assign it deliberately through the role-management API.

Role management is available when the built-in authentication API is enabled:

- `GET /api/v1/roles` and `GET /api/v1/roles/permissions`
- `POST /api/v1/roles`, `PUT /api/v1/roles/{key}`, and
  `DELETE /api/v1/roles/{key}`
- `GET /api/v1/users/{id}/roles` and `PUT /api/v1/users/{id}/roles`

Role creation, changes, deletion, and assignment require the
`roles:manage` permission. Built-in roles and the final role manager are
protected against removal. Role and permission changes take effect on the next request, including for
already-issued access tokens. Redis caches effective authorities for up to
five minutes. Each lookup first reads the tenant policy revision and the user's
membership revision from PostgreSQL, then checks Redis using a tenant-, user-,
and revision-specific key. A cache miss loads effective roles and permissions
from PostgreSQL. Updating a role's permissions advances the tenant policy
revision; changing a user's assignments or active/deleted state advances that
membership's revision in the same transaction. Old cache entries cannot match
the new revision and expire within five minutes. Redis read/write failures
fall back to PostgreSQL; database failures do not fall back to JWT claims.

New access tokens no longer contain a `roles` claim. Previously issued signed
tokens remain valid only while their session is active, and their role claim is
ignored; current authority is resolved from the PostgreSQL revision and
revision-matched Redis cache, with PostgreSQL as the source for cache misses.
If authoritative lookup fails, the request must not receive role-derived
authority from a token claim.

## Shared-mode security requirements

Before enabling shared multi-tenant runtime, JAVARA must demonstrate all of the
following:

1. Tenant identity comes from authenticated, server-validated context. A
   caller-supplied tenant ID alone never grants access.
2. Every tenant-owned read, create, update, delete, list, search, export, and
   background operation is scoped to the authorized tenant. Missing or
   ambiguous context fails closed.
3. Authorization is evaluated for the user or service principal within that
   tenant. Cross-tenant administration, if supported, is separately granted,
   authenticated, and audited.
4. Tenant isolation is preserved in database queries and constraints,
   service-client credentials and scopes, caches, queues, files, rate limits,
   logs, backups, and error responses.
5. Onboarding, membership changes, offboarding, recovery, retention, export,
   and deletion preserve the same isolation boundary.
6. Tests prove permitted same-tenant access and denied cross-tenant access for
   each data-access path, including list/search, privileged operations,
   service clients, and concurrent requests.

The current role assignment lookup is scoped to the internal default
organization. Service clients are not tenant-bound, and no tenant selection or
tenant context is propagated through requests. Therefore, choosing
`app.tenancy.mode=SHARED` fails fast instead of running with incomplete
controls.

Application-owned tables use the `javara_` prefix to distinguish them from
tables added by a consuming application. Spring-owned authorization and
WebAuthn persistence tables are also stored with this prefix; the application
adapts the framework JDBC operations to those names.

## Database mode lock and migration

At first startup, JAVARA records the selected mode in
`javara_installation_settings`. Subsequent startup checks require the
configuration to match the recorded value. Changing mode is a data migration,
not a configuration toggle. Do not edit the database marker manually to
bypass the startup check.

Migration V8 backfills users created by earlier migrations in this migration
set into the default organization and their `USER`/`ADMIN` role assignments.
All JAVARA-owned tables are created with the `javara_` prefix from their
initial migration; Flyway's `flyway_schema_history` retains its standard name.
This pre-release migration set is for a fresh, empty database and is not
compatible with databases carrying the previous unprefixed migration history.

## Assessment gate

Do not claim shared multi-company support until the tenant model is complete,
reviewed, and supported by reproducible isolation tests. Passing an assessment
of the single-organization profile does not prove shared-tenant isolation.
Customer-specific regulations and production configuration remain separate
assessment inputs.
