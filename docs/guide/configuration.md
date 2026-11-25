# Configuration

JAVARA reads local runtime settings from `config/application.yml` when started
through the repository scripts. The file is ignored by Git and should contain
only settings for that environment. Do not commit credentials, signing keys,
or real customer data.

Spring Boot environment variables can override application properties. For
example:

```text
APP_SECURITY_JWT_TRANSPORT=BEARER
APP_MIDDLEWARE_RATE_LIMIT_REGISTER_MAX_REQUESTS=5
APP_TIME_ZONE=Asia/Jakarta
SPRING_DATA_REDIS_HOST=localhost
```

## Application timezone

Set `app.time-zone` to a Java/IANA timezone ID, for example
`Asia/Jakarta`. It defaults to `UTC`; `make setup` and `make install` ask for
the timezone and write it into `config/application.yml`. You can also set
`APP_TIME_ZONE` in the environment.

JAVARA uses this zone on its injected `Clock` for local-calendar calculations.
Timestamps, JWT issue/expiry times, and database `Instant` values remain
absolute instants and are not shifted by this setting. Format timestamps in
the user's timezone at the client when they are user-specific.

## Database and Redis

Configure PostgreSQL under `spring.datasource` and Redis under
`spring.data.redis`. `make setup` writes these values for you. The application
needs both services: PostgreSQL stores users and refresh-token hashes; Redis
stores short-lived rate-limit counters.

Use separate credentials and service instances for development, testing, and
production. Do not use the dedicated integration-test database as the
application database.

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

Provide `SMTP_USERNAME` and `SMTP_APP_PASSWORD` through your environment or
secret manager; never put SMTP credentials in Git, a committed YAML file, or
logs. `make setup` and `make install` ask for SMTP host and port and write
credential placeholders, but do not collect or store the credentials. Their
generated public base URL is `http://localhost:8080`; replace it with the
externally reachable HTTPS URL before using a production-like profile.

At startup, JAVARA fails with an actionable message if email is enabled but
the SMTP host, sender address, or a required authentication credential is
missing. With `prod`, `production`, or `staging`, email cannot be disabled,
SMTP must use STARTTLS or SSL, and `app.email.public-base-url` must use HTTPS.
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
```

Use a strong, unique signing key for each environment. Do not copy the example
value above into a real deployment. The key must be Base64-encoded and decode
to at least 32 bytes. `make setup` and `make install` generate one for local
use. To generate a key for manual local configuration, run:

```bash
openssl rand -base64 32
```

Never commit signing keys or passwords.

The browser adapter is one optional client module under `sdk/browser`; it does
not change the HTTP API contract for native or service clients. Service-to-
service authentication is a separate OAuth 2.0 Client Credentials flow and
does not use user refresh tokens.

Refresh tokens rotate on refresh. The client must replace the old refresh
token with the newly returned one. Password changes revoke active refresh
tokens; already-issued access tokens remain valid until their short expiry.

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
`Retry-After` header. Redis is required at startup even if all auth limiters
are disabled. JAVARA checks Redis connectivity before accepting requests and,
when any auth limiter is enabled, also runs a rate-limit operation against
Redis. If Redis becomes unavailable after startup, a limited request returns
`503`; JAVARA does not silently bypass the limit. Redis connection and
command timeouts should be bounded; the generated setup uses two-second
timeouts.

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
