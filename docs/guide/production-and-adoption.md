# Production and Business Adoption

JAVARA can provide a useful starting point for applications that need basic
user registration, login, and user management. It is not a complete identity
provider or a finished business platform. Evaluate the gaps below against
your product before using real accounts or sensitive data.

## What is included

- PostgreSQL-backed user records and Flyway migrations
- Public USER registration and configurable initial ADMIN setup
- Bearer-token or HttpOnly-cookie authentication
- Rotating, revocable refresh tokens
- Password hashing and legacy-hash upgrade support
- USER/ADMIN authorization and same-user checks for ID-based operations
- Redis-based per-IP limits for login, registration, and refresh
- Unit and isolated PostgreSQL/Redis integration tests

## Important product boundaries

The current service does not provide:

- Organization or tenant isolation
- Business-specific roles, permissions, or approval workflows
- Email verification, password recovery, or multi-factor authentication
- Audit history for administrator actions
- A PATCH endpoint or versioned API beyond `/api/v1`
- A complete health/metrics and operational monitoring solution

Do not use a shared JAVARA instance for multiple customer organizations
without implementing and testing tenant isolation at every API and database
boundary. Do not treat the current ADMIN/USER roles as a substitute for your
business authorization model.

Public registration is enabled by the current security configuration. If
your business requires invitation-only or administrator-created accounts,
add and test that control before exposing the service.

## Before production

### Secrets and accounts

- Put database, Redis, bootstrap, and JWT signing secrets in a managed secret
  store; do not commit them in YAML or environment files.
- Use a unique signing key for each environment and plan key rotation.
- Disable bootstrap-admin after creating the first ADMIN.
- Use a strong initial ADMIN password and rotate it after first access.

### Network and browser security

- Serve traffic over HTTPS.
- Keep `server.request.header.forwarded.strategy: none` for direct
  connections. Set it to `framework` only behind a trusted proxy that
  overwrites client-supplied forwarding headers, and block public access to
  the application port.
- Test that changing `X-Forwarded-For` cannot bypass rate limits at the edge
  proxy, not only in application-level tests.
- Keep Redis available before starting JAVARA. It is a required service even
  when authentication rate limiting is disabled; startup fails before the
  application begins accepting requests if Redis is unavailable. When an
  authentication rate limit is enabled, startup also verifies the Redis
  rate-limit operation.
- Restrict CORS to known origins. Do not use wildcard origins with cookies.
- Use cookie transport and CSRF protection where appropriate for browser
  clients; test `SameSite`, `Secure`, and domain settings in the real
  deployment.
- Configure firewalls so PostgreSQL and Redis are not publicly reachable.

### Data and operations

- Use managed PostgreSQL and Redis with authentication, encryption, backups,
  monitoring, and tested restore procedures.
- Review every Flyway migration against a production-like copy before release.
- Add health checks, metrics, structured logs, alerting, and request tracing.
- Define retention and privacy policies for user and refresh-token data.
- Load-test the chosen rate limits and connection-pool settings.
- Run integration tests in CI against disposable or dedicated test services.

## Suggested adoption path

1. Run the local [getting-started guide](getting-started.md).
2. Use the [first API workflow tutorial](../tutorial/first-api-workflow.md)
   to validate the client-to-API flow.
3. Write down required roles, tenant boundaries, registration rules, and
   account recovery requirements.
4. Implement missing product controls and add tests before connecting
   production data.
5. Review the [configuration guide](configuration.md) and security settings
   with the team responsible for deployment.
6. Deploy first to a staging environment with synthetic data.

JAVARA is an open-source foundation that teams can adapt. It does not certify
that a deployment meets a specific business, privacy, or regulatory
requirement.
