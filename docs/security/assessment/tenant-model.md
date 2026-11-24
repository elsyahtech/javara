# Shared Multi-Tenant Security Decision

## Decision status

- Target model: shared multi-tenant deployment.
- Selected: 2026-11-24.
- User identity model: one global user may belong to multiple companies,
  selected: 2026-11-24.
- Implementation status at assessed source commit
  `aacdb7858552fe2cfd35eb40ae04808d0e33c120`: not evidenced.
- This record defines security requirements for the target; it is not an
  implementation design or a claim that tenant isolation exists.

## Context

JAVARA is intended to be a reusable platform for multiple companies and
customer solutions. A shared deployment can reduce operating cost and support
dynamic provisioning, but it makes tenant isolation a critical security
boundary. A missing or incorrectly propagated tenant context could expose or
modify another company's data.

## Current baseline gap

The inspected baseline has a global user role and no tenant membership model:

- [`UserEntity`](../../../src/main/java/com/elsyah/tech/javara/entity/user/UserEntity.java)
  stores one global role per user.
- [`UserRole`](../../../src/main/java/com/elsyah/tech/javara/entity/user/UserRole.java)
  contains only `USER` and `ADMIN`.
- [`AccessTokenService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AccessTokenService.java)
  issues a user token with identity and global role claims, but no tenant
  context.
- The [user migration](../../../src/main/resources/db/migration/V1__create_user.sql)
  has global email uniqueness and no membership relation; the
  [OAuth client migration](../../../src/main/resources/db/migration/V3__create_oauth2_client_credentials.sql)
  does not associate clients with a tenant.

Global user identity is compatible with the selected product model, but
company membership, tenant-scoped authorization, and tenant-bound service
clients are not evidenced in this baseline.

## Proposed logical model

This is a proposal for review, not an approved implementation design:

- Keep a global user identity and add a company/tenant entity plus a
  user-to-tenant membership relation.
- Store tenant-scoped membership status and roles separately from any
  platform-wide administrative role.
- Establish an active tenant only after verifying the user's membership. A
  tenant ID supplied by the caller must not establish authorization.
- Bind service clients and their allowed scopes to an explicit tenant.
- Propagate the authorized tenant context to every data-access operation and
  include tenant ownership in constraints for tenant-owned data.
- Evaluate database-enforced isolation (such as row-level security) as
  defense-in-depth; do not treat it as a substitute for application-level
  authorization and tests.

## Required security invariants

The design and implementation must demonstrate that:

1. Tenant identity is derived from authenticated, server-validated context.
   A caller-supplied tenant identifier alone must never grant access to a
   tenant.
2. Every tenant-owned read, create, update, delete, list, search, export, and
   background operation is scoped to the authorized tenant. Missing or
   ambiguous tenant context fails closed.
3. Authorization is evaluated for the user or service principal within the
   tenant context. Any explicitly supported cross-tenant administrative access
   is narrowly granted, separately authenticated, and auditable.
4. Tenant isolation is preserved across database queries and constraints,
   OAuth/service clients, caches, queues/events, files, rate limits, logs, and
   error responses.
5. Company onboarding, membership changes, offboarding, data retention,
   export/deletion, tenant configuration, and recovery procedures preserve the
   same isolation boundary.
6. Tests demonstrate both permitted same-tenant access and denied
   cross-tenant access for each data-access path, including list/search,
   privileged operations, service clients, and concurrent requests.

These are platform security requirements. The final implementation choices
(for example, database row-level security, application-level tenant scoping,
or a combination) require a separate design review and defense-in-depth
analysis.

## Design decisions still required

- How tenant context is established and propagated through HTTP, jobs, and
  service-to-service requests.
- How membership roles/scopes are represented and how membership changes
  revoke or constrain existing sessions and access tokens.
- Whether user email and service-client identifiers are globally unique or
  tenant-scoped beyond the selected global user-identity model.
- How tenant ownership is enforced in persistence, including query filters,
  database constraints, and any row-level controls.
- Whether cryptographic keys, secrets, quotas, logs, backups, and retention
  policies are shared or tenant-specific.
- How migrations and existing global users/clients are handled when enabling
  tenants.

Each decision must have a documented rationale, threat analysis, and tests
before the corresponding control is marked as verified.

## Assessment gate

Do not claim shared multi-company support until the tenant model is documented,
implemented, reviewed, and supported by reproducible isolation tests. A
successful ASVS assessment of the existing single-company baseline would not
by itself prove this capability. Customer-specific regulations and production
configuration also remain separate assessment inputs.
