# Preliminary Repository Evidence Inventory

## Status and baseline

This is an initial source inventory, not a completed security assessment.
No requirement is marked as passing or failing.

- Assessed source commit: `aacdb7858552fe2cfd35eb40ae04808d0e33c120`
- ASVS baseline: v5.0.0 Level 3
- Inventory date: 2026-11-24
- Scope and limitations: [assessment scope](scope.md)

## Multi-company capability

### Observation

No explicit tenant, company, organization, or multi-tenant identifiers were
found in the initial search of `src/main/java`, `src/main/resources`, and
`src/test/java`. The inspected persistence model and queries also show no
company boundary:

- [`UserEntity`](../../../src/main/java/com/elsyah/tech/javara/entity/user/UserEntity.java)
  contains user identity, credentials, role, status, and timestamps, but no
  tenant/company association.
- [User schema migration](../../../src/main/resources/db/migration/V1__create_user.sql)
  defines a global `users` table without a tenant/company key or membership
  table; its active-email unique index is global rather than tenant-scoped.
- [`UserRepository`](../../../src/main/java/com/elsyah/tech/javara/repository/user/UserRepository.java)
  has user queries without a tenant parameter; `findAllUsersPage` lists users
  globally.
- [Refresh-token migration](../../../src/main/resources/db/migration/V2__create_refresh_tokens.sql)
  associates refresh tokens with a user, not a company.
- [OAuth client migration](../../../src/main/resources/db/migration/V3__create_oauth2_client_credentials.sql)
  defines OAuth clients and authorizations without a company/tenant association;
  client IDs are unique globally.

This is a bounded repository observation, not proof that no external
integration or customer-specific extension can add tenancy outside the
assessed baseline.

### Preliminary interpretation

The assessed baseline does not provide repository evidence of a multi-company
tenant-isolation model. Treat multi-company support as **not yet evidenced** and
do not make a customer-facing isolation claim based on this code alone. This is
a product capability gap to resolve and verify; it is not, by itself, an ASVS
pass/fail determination or a confirmed vulnerability.

The tenant model remains an open architecture decision. Before implementation
or assessment, document whether JAVARA will support shared multi-tenancy,
dedicated deployments, or both, and define the expected isolation boundary for
users, service clients, authorization data, configuration, logs, and backups.

## Security evidence candidates for later verification

These are source locations to assess next. Their presence does not establish
that any requirement is satisfied:

- API authorization, stateless sessions, CSRF, and security headers:
  [`ApiSecurityConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/ApiSecurityConfiguration.java).
- JWT transport and key/TTL configuration:
  [`JwtProperties`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/JwtProperties.java)
  and [`JwtTokenConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/JwtTokenConfiguration.java).
- Login, refresh, logout, and refresh-token rotation:
  [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java)
  and [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java).
- Service-client authorization:
  [`ServiceAuthorizationServerConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/ServiceAuthorizationServerConfiguration.java).
- Password hashing and legacy hash verification:
  [`PasswordHashService`](../../../src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java).
- Existing authorization and cookie integration tests:
  [`SecurityAuthorizationIntegrationTests`](../../../src/test/java/com/elsyah/tech/javara/integration/SecurityAuthorizationIntegrationTests.java)
  and [`CookieSecurityIntegrationTests`](../../../src/test/java/com/elsyah/tech/javara/integration/CookieSecurityIntegrationTests.java).

## Next repository assessment step

Resolve the multi-company architecture decision before mapping tenant-specific
controls. Then verify authentication, authorization, token lifecycle, and API
security behavior against the versioned ASVS requirements, recording the exact
source/test evidence and leaving unverified controls as `Pending`.
