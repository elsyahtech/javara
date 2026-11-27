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
- SMTP email verification for public registrations and security notifications
- Optional email OTP and WebAuthn passkey MFA with deployment-wide policy
- Configurable common and compromised-password checks using operator-managed
  offline datasets
- Unit and isolated PostgreSQL/Redis integration tests

## Important product boundaries

The current service does not provide:

- Organization or tenant isolation
- Business-specific roles, permissions, or approval workflows
- SMS/WhatsApp MFA delivery, phone-number enrollment, and tenant-specific MFA
  policies
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

### Email delivery

Public registrations cannot sign in until email verification succeeds.
Existing accounts and bootstrap/admin-created accounts remain verified.
Configure SMTP credentials through a secret manager or environment variables,
set `app.email.public-base-url` to the public HTTPS address, and activate a
production profile. Startup rejects missing SMTP configuration, insecure
production transport, or an HTTP public URL with an actionable error.

Verification links contain a single-use token in the query string. Redact the
`token` parameter from reverse-proxy and access logs. Delivery happens after
the database transaction commits; SMTP errors are logged without message
contents, but there is no durable outbox or retry mechanism yet. Registration
verification can be requested again, while password-change, email-change, and
new-session security notices may be lost during an SMTP outage. Monitor mail
delivery failures and plan durable delivery before relying on these notices as
an operational alert.

### Multi-factor authentication

Email OTP and WebAuthn passkeys are available as configurable MFA methods.
MFA is optional by default and can be required deployment-wide. Select only
methods that have a configured provider; the application rejects SMS or
WhatsApp in `allowed-methods` until those delivery integrations and phone
enrollment are implemented. This is a global deployment policy, not a
per-tenant control.

For production WebAuthn, configure the relying-party ID for the public site
and exact HTTPS origins. The production-like profiles reject localhost
relying-party IDs and non-HTTPS origins. Verify the browser and authenticator
flows in the actual deployment before enabling passkeys for users.

Email OTP is not phishing-resistant. WebAuthn support does not by itself
establish that a deployment meets ASVS Level 3: configure and verify the
required factor policy, supported authenticators, origin/RP settings, and
recovery process. Login MFA currently does not provide step-up verification
for sensitive operations. Do not claim Level 3 MFA compliance based only on
the presence of these code paths.

### Password policy datasets

Password policy is enabled by default and is required for the `prod`,
`production`, and `staging` profiles. JAVARA includes only tiny illustrative
sample files for local setup, not production password or breach datasets. The
deployment owner must provide approved data, record its provenance/licence,
and mount it read-only before starting the application.

The default configuration expects:

| Property | Default path | Format |
|---|---|---|
| `app.security.password-policy.common-passwords-file` | `./config/security/common-passwords.txt` | UTF-8, one exact password per line, at least 3,000 distinct entries of 8–72 characters |
| `app.security.password-policy.breached-password-hashes-file` | `./config/security/breached-password-hashes.txt` | UTF-8, one SHA-1 hex digest per line; an optional `:count` suffix is accepted |

`make install` and `make setup` create small illustrative files at these paths
when the files are absent. The generated local configuration activates the
`local` profile and sets `app.security.password-policy.sample-data: true`, so
`make run` starts and demonstrates both checks on a clean local setup. The
common-password examples and demonstration hash are not a sourced or licensed
security corpus; they do not represent meaningful breach coverage. Startup
logs a warning in this mode. The seed script preserves files already supplied
by the operator. For a smoke test, the sample denylist blocks `password` and
the sample hash matches `demo-breach-check-password`; neither example is a
claim that the value came from an external breach corpus.

Both paths are configurable. The current implementation loads these datasets
into application memory at startup, so choose a starter/approved subset that
fits the JVM heap. Its breach-check coverage is only as complete and current as
the supplied file; a subset is not a claim that every known breach is covered.
Very large full-corpus deployments need a separately sized disk-backed lookup
implementation before enabling that corpus.

Update datasets through a reviewed release/deployment process: verify source
terms and licensing, provenance, freshness, file integrity/checksum, and
coverage; then restart or roll the application so the new files are loaded.
Do not put production plaintext breach corpora in the repository, container
image, logs, or support tickets. Operator-provided denylist files contain
plaintext passwords and must have restricted read access. The breach file
contains hashes; it is only for local membership checks and must not be used
as a password-storage mechanism.

Before using approved datasets, replace the sample files and set
`app.security.password-policy.sample-data: false`. Production, staging, and
any profile other than `local`, `dev`, or `test` reject sample mode at startup.
With sample mode disabled, startup requires at least 3,000 distinct common
password entries. Production deployments should explicitly activate `prod`,
`production`, or `staging`, install approved datasets, and verify the resolved
paths and file permissions.

When required data is absent or invalid, startup stops with an actionable
`Password policy startup validation failed` message. For example:

```text
Password policy startup validation failed: Common-password denylist is missing or unreadable at '/srv/javara/config/security/common-passwords.txt'. Provide a UTF-8 file with at least 3,000 distinct passwords and configure app.security.password-policy.common-passwords-file.
```

Mount or correct the named dataset and restart. Do not disable the control to
make a production deployment start. Disabling it is allowed only for isolated
local development or tests; JAVARA rejects that setting when `prod`,
`production`, or `staging` is active. The test-only override lives in
`src/test/resources/application.yml`.

When a submitted password matches either dataset, the API responds with
`400 Bad Request` and asks the user to choose a password that is not common or
known to be compromised. It does not disclose which list matched or log the
password.

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
  monitoring, and tested restore procedures. JAVARA's optional database
  backup commands are described in the [getting-started guide](getting-started.md#optional-database-backups);
  they back up one database and do not replace provider-level recovery plans.
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
