# Configuration

JAVARA reads local runtime settings from `config/application.yml` and optional
area-specific files under `config/` when started through the repository
scripts. These files are ignored by Git and should contain only settings for
that environment. Do not commit credentials, signing keys, certificates, or
real customer data.

Spring Boot environment variables can override application properties. For
example:

```text
APP_SECURITY_JWT_TRANSPORT=BEARER
APP_MIDDLEWARE_RATE_LIMIT_REGISTER_MAX_REQUESTS=5
APP_TIME_ZONE=Asia/Jakarta
SPRING_DATA_REDIS_HOST=localhost
```

## Runtime modules

JAVARA uses one application artifact. Runtime switches activate only the
selected built-in modules; they do not remove code from the package. Existing
configurations retain the standard profile because these switches default to
`true`:

```yaml
app:
  features:
    auth-api: true
    resource-server: true
    rate-limiting: true
  security:
    service-auth:
      enabled: true
```

`make setup` asks which modules to enable and requests only their required
settings. It fails before writing the file if the selection is inconsistent.
The dependency rules are:

- `auth-api` requires `resource-server`. In production-like profiles, it also
  requires email delivery for verification, MFA, and recovery messages.
- `service-auth` (`app.security.service-auth.enabled`) requires
  `resource-server`.
- Authentication rate limiting applies to JAVARA's built-in auth routes and
  requires Redis only when at least one limiter is enabled. It is inactive when
  `auth-api` is off.

To keep JWT protection while removing JAVARA's registration, login, account,
and service-client management APIs, use:

```yaml
app:
  features:
    auth-api: false
    resource-server: true
    rate-limiting: false
  security:
    service-auth:
      enabled: false
    jwt:
      transport: BEARER
      signing-key: "<base64 key with at least 32 decoded bytes>"
  email:
    enabled: false
```

With `auth-api` disabled, its built-in routes return `404`; JWT-protected
application routes still require a valid JAVARA-compatible token. The current
resource server validates JAVARA's token claims and active user sessions or
registered service clients; it is not a general-purpose external-issuer/JWKS
integration. This profile does not issue user tokens, so another trusted
JAVARA-compatible identity deployment must issue them and share the required
active session data.

## Organization mode and roles

`make setup` and `make install` ask whether this deployment serves one
organization or multiple organizations:

```yaml
app:
  tenancy:
    mode: SINGLE
```

`SINGLE` is the supported default. JAVARA stores an internal default
organization and associates user roles with that organization. `SHARED` can
be selected to make the intent explicit, but the application intentionally
fails at startup until tenant selection, authorization, and data isolation are
implemented end to end. Do not use `SHARED` for a live deployment.

The selected mode is recorded in PostgreSQL on first startup. Changing the
configuration later without a migration causes startup to fail with a
diagnostic message.

When `auth-api` is enabled, role management endpoints are available to callers
with the `roles:manage` permission:

| Method and path | Purpose |
|---|---|
| `GET /api/v1/roles` | List built-in and custom roles |
| `GET /api/v1/roles/permissions` | List permission keys that can be assigned |
| `POST /api/v1/roles` | Create a custom role |
| `PUT /api/v1/roles/{key}` | Update a custom role's name, description, and permissions |
| `DELETE /api/v1/roles/{key}` | Delete an unassigned custom role |
| `GET /api/v1/users/{id}/roles` | Read a user's roles |
| `PUT /api/v1/users/{id}/roles` | Replace a user's role assignments |

Built-in `USER` and `ADMIN` roles cannot be changed or deleted. A user always
retains the baseline `USER` assignment. Custom roles can use only permission
keys returned by the permissions endpoint; adding new endpoint capabilities
requires a reviewed application and database change.
User responses include the current `roles` list. The legacy singular `role`
field remains as a `USER`/`ADMIN` compatibility summary; use `roles` for current
assignments.

The JWT `roles` claim is not used for authorization. PostgreSQL remains the
source of truth for role assignments and permissions. On each authorization
lookup, JAVARA reads the active organization's and user's authorization
revisions from PostgreSQL, then checks Redis for the matching cached role and
permission snapshot. A cache miss loads the snapshot from PostgreSQL and
caches it for up to five minutes. Role-definition permission changes advance
the organization revision; assignment and account-status changes advance the
user-membership revision in the same database transaction. Therefore the next
request after a successful change uses a new cache key and cannot receive the
old snapshot, including on another application instance. Old cache entries
expire after five minutes.

Redis is only a cache for authorization data. If a Redis cache read or write
fails, JAVARA uses PostgreSQL; if PostgreSQL cannot provide the authoritative
revision or role data, the request does not gain access from an old JWT claim.
This cache fallback is separate from authentication rate limiting, which has
its own Redis availability policy.

If `resource-server` is disabled, the API filter chain does not validate bearer
tokens and unmatched routes are public. Method-level `@PreAuthorize` checks
remain active, so routes that require an authenticated principal may still
deny requests. Do not disable this module for protected endpoints.

## Application identity and email attribution

Set `app.name` to the product or service name shown in operational startup
messages, email subjects and bodies, browser page titles, and the WebAuthn
relying-party label. `make setup` and `make install` ask for this name and use
`JAVARA` if no other name is supplied.

```yaml
spring:
  application:
    name: ${app.name}
app:
  name: Customer Portal
  branding:
    powered-by: JAVARA
```

`app.branding.powered-by` adds a separate “Powered by …” line to notification
emails. It defaults to `JAVARA`; enter `none` during setup or set this property
to an empty string to omit that attribution. JAVARA's internal package,
database, and configuration identifiers are not renamed.

## Application timezone

Set `app.time-zone` to a Java/IANA timezone ID, for example
`Asia/Jakarta`. It defaults to `UTC`; `make setup` and `make install` ask for
the timezone and write it into `config/application.yml`. You can also set
`APP_TIME_ZONE` in the environment.

JAVARA uses this zone on its injected `Clock` for local-calendar calculations.
Timestamps, JWT issue/expiry times, and database `Instant` values remain
absolute instants and are not shifted by this setting. Format timestamps in
the user's timezone at the client when they are user-specific.

## Database connection and pooling

`make setup` configures the initial PostgreSQL connection. Use `make db-setup`
later to update the connection or tune its HikariCP pool without rewriting
module, email, or token settings. The command writes
`config/database.yml`, prompts before replacing it, and stores the password in
that Git-ignored file with restricted permissions. It adds the fragment to
`spring.config.import` in `config/application.yml` when needed.

The standard pool values are:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      validation-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
```

Timeouts are milliseconds in YAML; `make db-setup` accepts values such as
`30s`, `5s`, and `10m`. Pool size must fit the database connection budget:
account for every JAVARA replica and reserve connections for migrations,
administration, and other services. The defaults are starting values, not a
capacity recommendation for every deployment.

Redis under `spring.data.redis` is required only when authentication rate
limiting is enabled. `make setup` asks for Redis settings only in that case.
PostgreSQL stores application records; Redis stores short-lived rate-limit
counters. Use separate credentials and service instances for development,
testing, and production. Do not use the dedicated integration-test database
as the application database.

`make db-setup` validates pool sizes and timeout ranges before writing. Its
configuration is imported after `config/application.yml`, so these database
settings override the initial database values while leaving other settings
unchanged.

## Email delivery and verification

Email delivery is enabled by default because public registrations require
email verification. Configure a provider-neutral SMTP connection in
`config/application.yml`; for example, Gmail can be used for local or
low-volume testing with an App Password:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_APP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
app:
  email:
    enabled: true
    from-address: ${SMTP_USERNAME}
    verification-token-ttl: 24h
    public-base-url: https://api.example.com
```

`make setup` asks for the SMTP host, port, username, and App Password, then
writes the credentials directly into the ignored local `config/application.yml`.
The App Password is entered without terminal echo. `make install` asks only for
the host and port and writes credential placeholders; for that flow, provide
`SMTP_USERNAME` and `SMTP_APP_PASSWORD` through the environment/secret manager
or replace the placeholders locally.

The local YAML option stores credentials as plaintext. The `config/` Git ignore
rule prevents accidental tracking; it does not encrypt the file or protect it
from local users, backups, or file-sharing tools. If storing credentials there,
restrict file access (for example, `chmod 600 config/application.yml`), use a
Gmail App Password rather than the account password, keep the file out of
backups/screenshots/logs and shared folders, and rotate the App Password if the
file may have been exposed. Verify the ignore rule with
`git check-ignore -v config/application.yml`; never force-add this file. For
production, use a managed secret store rather than plaintext YAML.

The generated public base URL is `http://localhost:8080`; replace it with the
externally reachable HTTPS URL before using a production-like profile.

At startup, JAVARA fails with an actionable message if email is enabled but
the SMTP host, sender address, or a required authentication credential is
missing. With `prod`, `production`, or `staging`, email cannot be disabled
while `auth-api` is enabled; SMTP must use STARTTLS or SSL, and
`app.email.public-base-url` must use HTTPS. For local development, email can
be disabled, but verification and security notifications will not be sent.
Automated tests use the isolated `src/test/resources/application.yml`
configuration and do not load SMTP credentials from `config/application.yml`.
Email-specific tests use a test delivery implementation rather than a live SMTP
provider.
Verification tokens are single-use, stored only as SHA-256 hashes, and expire
after `app.email.verification-token-ttl` (24 hours by default).

Verification URLs contain the token in a query parameter. Configure reverse
proxy and access logs to redact the `token` parameter. Email is sent after the
database transaction commits; delivery failures are logged without message
contents, but there is currently no durable outbox or automatic retry.
Registration verification can be requested again through the resend endpoint.

## Initial ADMIN account

The bootstrap settings are:

```yaml
app:
  security:
    bootstrap-admin:
      enabled: true
      email: admin@example.com
      password: "<provide-a-strong-local-secret>"
```

The account is created only when the configured email is unused. Once the
ADMIN can sign in, disable bootstrap and remove its password from the config.
Bootstrap does not promote a USER account to ADMIN.

## Authentication tokens

JAVARA supports two token transports:

- `COOKIE` (default): login returns HttpOnly cookies for access and refresh.
  The browser adapter handles CSRF, refresh, and one retry after an expired
  access token.
- `BEARER`: login returns access and refresh tokens in the response.
  The client sends the access token using
  `Authorization: Bearer <access-token>`.

Select the transport with:

```yaml
app:
  security:
    jwt:
      transport: COOKIE
      signing-key: "<use-a-unique-secret-from-your-secret-manager>"
      access-token-ttl: 15m
      refresh-token-ttl: 30d
      refresh-token-rotation-enabled: true
```

Use a strong, unique signing key for each environment. Do not copy the example
value above into a real deployment. The key must be Base64-encoded and decode
to at least 32 bytes. `make setup` and `make install` generate one for local
use. To generate a key for manual local configuration, run:

```bash
openssl rand -base64 32
```

`refresh-token-ttl` is the lifetime of a refresh token, but the session policy
can impose a shorter total lifetime:

- `absolute-lifetime-enabled: true` (the secure default) sets a maximum session
  duration measured from sign-in, using `absolute-lifetime` (12 hours by
  default, and at most 12 hours). Refreshing rotates the token but never moves
  that end time. Therefore `refresh-token-ttl: 30d` does not produce a 30-day
  session while the maximum session duration is set to 12 hours.
- `absolute-lifetime-enabled: false` makes the configured
  `refresh-token-ttl` the fixed maximum session lifetime from login. With the
  default `30d`, a session can last up to 30 days, but repeated refreshes do
  not extend that original deadline. The unused `absolute-lifetime` value is
  ignored in this mode.

The idle timeout is a separate rule: when enabled, inactivity can end a
session sooner than either total lifetime. Defaults are 15 minutes idle and a
12-hour maximum session duration. `make security-setup` lets each deployment
choose whether to enable idle expiry, the maximum session duration, and
refresh-token rotation.

Refresh-token rotation is enabled by default. When enabled, a successful
refresh returns a new refresh token and invalidates the old one; reuse of a
rotated token is treated as possible theft and revokes the user's active
sessions. When disabled, refresh returns the same refresh token and only issues
a new access token. Its expiry does not move: it remains fixed from login and
is also capped by the session's end time. Disabling rotation improves client
simplicity but removes replay detection; a stolen token can be reused until it
expires or its session is revoked. This option is independent of idle timeout
and maximum session duration.

Run `make security-setup` and restart JAVARA for the new settings to take
effect. A configuration change does not restore sessions that were already
revoked. For example, if refresh was attempted after an enabled idle timeout
had expired, JAVARA revoked that session; disabling idle expiry later cannot
make its old refresh token valid again.

Maximum session end times are saved when a session is created. Existing
sessions keep their saved end time after a configuration change; sign in again
to test the new maximum session duration setting. To test disabled idle expiry, wait
longer than the configured idle period but less than the session's fixed end
time. With the default 15-minute access-token lifetime, a protected request may
first return `401`; refreshing with the new session's current refresh token
should succeed if the session is otherwise valid. Do not retry a refresh token
that already returned `401`: a revoked-token reuse is treated as possible theft
and can revoke the user's other active sessions too.

### What each session setting does

These controls are independent. Changing one does not disable the others.
The defaults below favor a secure production baseline; `make security-setup`
lets each deployment choose different behavior and shows the main trade-off.

| Setting | Enabled | Disabled |
|---|---|---|
| `idle-timeout-enabled` (default `true`, 15 minutes) | A successful authenticated request or refresh updates the server's last-activity time. If the idle period passes, refresh is rejected with `401` and the session is revoked; the user must sign in again. | Inactivity alone does not end the session. An expired access token can still be refreshed while the session's total lifetime and other checks remain valid. This is more convenient for idle users, but an unattended session remains usable longer. |
| Maximum session duration (`absolute-lifetime-enabled`, default `true`, 12 hours) | The session has a fixed end time measured from sign-in. Refresh does not move it. When it ends, refresh is rejected and the user must sign in again, even if they were recently active. | `refresh-token-ttl` becomes the session's fixed maximum lifetime measured from sign-in (30 days by default). This is not a sliding window: refresh does not add another TTL. |
| `refresh-token-rotation-enabled` (default `true`) | Each successful refresh issues a new access token and a new refresh token. Save the new refresh token and discard the old one. Reusing an old, rotated token is treated as possible theft and can revoke the user's active sessions. | Each refresh issues a new access token but keeps the same refresh token. The client must keep using that token; its expiry does not move. Replay detection is unavailable, so a stolen token can be reused until it expires or the session is revoked. |

`max-concurrent-sessions` is a separate limit (five by default). At the limit,
a new login is rejected with `409`; JAVARA does not silently log out another
device.

The secure defaults are designed as a production-oriented baseline intended
to support ASVS Level 3 practices. Defaults do not certify a deployment or
guarantee that it meets ASVS Level 3: configuration choices, integrations,
operations, and independent verification still matter. Disabling a control
changes the protections active in that deployment and may affect its security
assessment. Choose settings for the users and risks involved; JAVARA provides
the controls and explains their effects, while the deployment owner decides
which behavior is appropriate.

Successful authenticated requests and successful refreshes update the
server-recorded last-activity time. This measures request activity, not
keyboard or mouse activity; background polling or token refreshes can keep a
session active while the user is away.

```yaml
app:
  security:
    session:
      idle-timeout-enabled: true
      idle-timeout: 15m
      absolute-lifetime-enabled: true
      absolute-lifetime: 12h
      max-concurrent-sessions: 5
```

Never commit signing keys or passwords.

The browser adapter is one optional client module under `sdk/browser`; it does
not change the HTTP API contract for native or service clients. Service-to-
service authentication is a separate OAuth 2.0 Client Credentials flow and
does not use user refresh tokens.

When refresh-token rotation is enabled, the client must replace the old
refresh token with the newly returned one. When disabled, keep using the same
refresh token; only the access token changes. Protected requests validate the
access token's active server-side session, so logout, password changes, account
disablement/deletion, and session termination reject an already-issued access
token on its next protected request. An access-token `401` does not always mean
the user must sign in: refresh may succeed if the session is still valid. The
API guide's [token-expiry scenarios](api-usage.md#what-happens-when-a-token-expires)
describes what clients should do for each outcome.

## Multi-factor authentication

MFA is enabled but optional by default. JAVARA currently supports email
one-time codes and WebAuthn passkeys. The deployment-wide `allowed-methods`
setting limits which factors users can enroll or select at login; there is no
tenant-specific policy model.

```yaml
app:
  security:
    mfa:
      enabled: true
      required: false
      default-method: EMAIL_OTP
      allowed-methods:
        - EMAIL_OTP
        - WEBAUTHN
      challenge-ttl: 5m
      max-attempts: 5
      relying-party-id: auth.example.com
      allowed-origins:
        - https://app.example.com
```

Set `required: true` to require MFA for all accounts. Accounts without an
enrolled factor must enroll the configured default factor during their next
login before tokens are issued. Set `allowed-methods` to only `WEBAUTHN` and
`default-method: WEBAUTHN` when deployment policy requires passkeys. WebAuthn
requires the correct relying-party ID and exact browser origins; production,
staging, and `prod` profiles reject `localhost` relying-party IDs and
non-HTTPS origins. Use HTTPS for deployed applications.

After password verification, login returns `202 Accepted` with
`data.mfaChallenge` and does not issue access or refresh tokens. Clients may
send the optional `mfaMethod` field in the login JSON to select an enrolled,
deployment-allowed method; otherwise JAVARA uses the account's preferred
method (or the configured default for required enrollment). Complete the
challenge through the login MFA verification endpoint to receive tokens.
Access tokens record `amr` values (`pwd` plus `otp` for email, or `hwk` for
WebAuthn), and refresh rotation preserves them.

Authenticated users can inspect policy and enrolled factors at
`GET /api/v1/auth/mfa`. Enrollment, preference, and disable endpoints require
the current password where applicable. Enabling or changing a factor revokes
active sessions. Required policy prevents disabling the last enrolled factor.

SMS and WhatsApp enum values are reserved for future provider integrations;
there is currently no phone-number model or delivery provider. The application
fails at startup if either method is listed as allowed without a provider.
Email OTP is not phishing-resistant and does not, by itself, satisfy an ASVS
Level 3 phishing-resistant MFA requirement. MFA is a login control today; it
does not yet provide step-up verification for sensitive operations.

## Authentication rate limits

Each endpoint has its own setting group. Defaults are enabled and can be
changed without editing Java code:

```yaml
app:
  middleware:
    rate-limit:
      login:
        enabled: true
        max-requests: 10
        window: 1m
        key-prefix: javara:local:login
      register:
        enabled: true
        max-requests: 5
        window: 1h
        key-prefix: javara:local:register
      refresh:
        enabled: true
        max-requests: 30
        window: 1m
        key-prefix: javara:local:refresh
```

The counters use the client IP and a fixed time window. Use distinct key
prefixes for environments sharing Redis. Exceeded limits return `429` with a
`Retry-After` header. When any auth limiter is enabled, JAVARA checks Redis
connectivity and performs a rate-limit operation before accepting requests.
If Redis becomes unavailable after startup, a limited request returns `503`;
JAVARA does not silently bypass the limit. Redis is not required when all
limiters are disabled or `auth-api` is off. Redis connection and command
timeouts should be bounded; the generated setup uses two-second timeouts.

## Suspicious authentication and temporary account locks

Suspicious-authentication controls are enabled by default. Configure them
separately with `make security-setup`; this writes only to the ignored
`config/security.yml` and adds its optional import to
`config/application.yml`. `make setup` and `make install` remain focused on
initial application configuration. Deployment configuration can override
security settings without changing Java code. The wizard also lets a
deployment configure session idle expiry, maximum session duration, and the
maximum concurrent sessions per user. Idle expiry defaults to enabled at 15
minutes; it can be disabled or set to a different duration. The maximum session
duration is enabled by default at 12 hours and can be disabled. When disabled,
the refresh-token TTL becomes the session's fixed maximum lifetime from login.
When enabled, the configured maximum session duration cannot exceed 12 hours.
In the YAML configuration, this option is named `absolute-lifetime-enabled`.
All four
suspicious-authentication signals are enabled by default:

1. Repeated failed password sign-ins (5 attempts in 10 minutes by default).
2. A successful sign-in from a different network source than the last successful
   sign-in for that account.
3. Repeated failed MFA verifications (5 attempts in 10 minutes by default).
4. Password changes and MFA enrollment, preference, or disable operations.

```yaml
# config/security.yml
app:
  security:
    session:
      idle-timeout-enabled: true
      idle-timeout: 15m
      absolute-lifetime-enabled: true
      absolute-lifetime: 12h
      max-concurrent-sessions: 5
    suspicious-authentication:
      enabled: true
      failed-login:
        enabled: true
        threshold: 5
        window: 10m
        lock-duration: 15m
        action: LOCK_AND_NOTIFY
      new-source:
        enabled: true
        action: NOTIFY
        lock-duration: 15m
      failed-mfa:
        enabled: true
        threshold: 5
        window: 10m
        lock-duration: 15m
        action: LOCK_AND_NOTIFY
      security-change:
        enabled: true
        action: NOTIFY
        lock-duration: 15m
```

Each signal can be disabled independently, or all can be disabled with the
top-level `enabled` switch. `action` accepts `NOTIFY`, `TEMPORARY_LOCK`, or
`LOCK_AND_NOTIFY`. Thresholds, time windows, and temporary-lock durations are
configurable; windows are limited to 24 hours and locks to 7 days. The default
locks the account temporarily after repeated password or MFA failures, while
new-source and security-change events only notify. The successful source is
stored as a keyed hash, not as a raw IP address, and currently represents the
network address—not a device fingerprint. Only the most recent successful
source is compared.

Treat lockout settings as a risk decision: locking on a new source or security
change can block legitimate users and can be abused to deny service. Configure
trusted proxy handling before relying on client addresses behind a proxy; with
the default forwarded-header strategy, the direct peer address is used. SMTP
must be configured for email alerts. The user lock expires automatically;
the application does not permanently deactivate the account.

## TLS and mutual TLS

TLS and mTLS are opt-in. By default, JAVARA does not terminate HTTPS itself.
For a deployment where a reverse proxy or load balancer handles public HTTPS,
keep application TLS disabled and secure the private connection between the
proxy and JAVARA according to the deployment network design.

To terminate HTTPS in the application, run:

```bash
make tls-setup
```

Provide a readable PKCS12 or JKS keystore path. The path must not contain
whitespace. Keep the keystore outside the repository and mount it into the
runtime environment. Set `SERVER_SSL_KEY_STORE_PASSWORD` through the process
environment or deployment secret manager; the password is not written to the
configuration file. Missing or invalid TLS material prevents startup.
`make tls-setup` writes `config/tls.yml` and adds its optional import to
`config/application.yml` when needed.

Inbound mTLS additionally requires clients to present certificates trusted by
JAVARA. Configure server TLS first, then run:

```bash
make mtls-setup
```

When enabled, client authentication is required (`need`), and the truststore
must contain the issuing CA or client certificates. Set
`SERVER_SSL_TRUST_STORE_PASSWORD` through the process environment or secret
manager. Clients without a trusted certificate cannot establish a connection.
This setup covers **inbound HTTPS only**; outbound mTLS to downstream services
is not currently configured by these commands.

The mTLS command refuses to enable client authentication unless application
TLS is configured. Disable mTLS before disabling application TLS. Each setup
command writes its own ignored file (`config/tls.yml` or `config/mtls.yml`)
and adds the optional import to `config/application.yml` when needed. Protect
keystores and truststores as secrets, rotate them through your deployment
process, and never commit them.

## Reverse proxies and client IPs

On Spring Boot 4.2, configure forwarded headers with
`server.request.header.forwarded.strategy`. The older
`server.forward-headers-strategy` property is deprecated.

The generated local configuration binds the API to loopback and does not
trust forwarded headers:

```yaml
server:
  request:
    header:
      forwarded:
        strategy: none
```

With `none`, rate limits use the address of the direct network peer. This is
the safe default for a directly exposed application. If a reverse proxy is in
front, requests will share the proxy's address until proxy handling is
explicitly enabled.

Set the following only when the application is reachable exclusively through
a trusted reverse proxy or load balancer:

```yaml
server:
  request:
    header:
      forwarded:
        strategy: framework
```

Equivalent environment variable:

```text
SERVER_REQUEST_HEADER_FORWARDED_STRATEGY=framework
```

`framework` makes Spring honor forwarded headers; it does not verify which
proxy sent them. Restrict network access to the application port so only the
proxy can connect, and configure the public-facing proxy to discard
client-supplied forwarding headers and write canonical ones.

### Nginx at the public edge

When Nginx is the first trusted hop, overwrite the forwarding headers instead
of appending to values supplied by the client:

```nginx
location / {
    proxy_pass http://javara; # Replace with the private upstream/service name.
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
}
```

Do not expose the JAVARA listener publicly; allow connections to it only from
Nginx. If a CDN or load balancer sits in front of Nginx, trust only its known
source CIDRs and configure Nginx's real-IP handling before using
`$remote_addr`.

### Traefik behind an upstream load balancer

If Traefik receives requests from another load balancer, trust only that
load balancer's actual source addresses. Replace this documentation-only
address with the CIDR used in your network:

```yaml
entryPoints:
  websecure:
    address: ":443"
    forwardedHeaders:
      insecure: false
      trustedIPs:
        - "198.51.100.10/32"
```

Do not set `insecure: true`. Keep JAVARA's backend port private so clients
cannot bypass Traefik and send their own headers directly.

## Browser CORS and cookies

When a browser frontend calls JAVARA from another origin, list that exact
origin:

```yaml
app:
  web:
    cors:
      enabled: true
      allowed-origins:
        - https://app.example.com
      allow-credentials: true
```

Do not use `*` with credentials. Cookie transport also needs HTTPS in
production, a compatible `SameSite` setting, and CSRF protection. See the
[browser tutorial](../tutorial/browser-cookie-authentication.md) for a
complete request flow.

## Migrations

Flyway applies versioned files from `src/main/resources/db/migration` at
startup. Optional sample migrations and seed data belong outside that
directory so they do not run automatically. See
[Database migrations and development seed data](database-migrations-and-seeding.md)
for file naming, a complete example, and safe local instructions. Never edit
a migration that has already been applied to a shared database; add a new
migration instead. Back up important data before deploying a migration.

The initial schema defines user IDs as PostgreSQL `UUID` and timestamps as
`TIMESTAMPTZ` directly in V1 and V2. These migrations are intended for a fresh
database. They do not convert an existing schema; do not point an existing
database at this version without deliberately reconciling its schema and
Flyway history first.
