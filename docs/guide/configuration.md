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
SPRING_DATA_REDIS_HOST=localhost
```

## Database and Redis

Configure PostgreSQL under `spring.datasource` and Redis under
`spring.data.redis`. `make setup` writes these values for you. The application
needs both services: PostgreSQL stores users and refresh-token hashes; Redis
stores short-lived rate-limit counters.

Use separate credentials and service instances for development, testing, and
production. Do not use the dedicated integration-test database as the
application database.

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

- `BEARER` (default): login returns access and refresh tokens in the response.
  The client sends the access token using
  `Authorization: Bearer <access-token>`.
- `COOKIE`: the API places tokens in HttpOnly cookies. Browser requests that
  change data must also send a CSRF token.

Select the transport with:

```yaml
app:
  security:
    jwt:
      transport: BEARER
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
`Retry-After` header. If Redis is unavailable, the limited request returns
`503`; JAVARA does not silently bypass the limit.

## Reverse proxies and client IPs

The setup files configure:

```yaml
server:
  forward-headers-strategy: framework
```

This allows Spring to resolve the client address from proxy forwarding
headers. Enable it only when the application is behind a trusted reverse proxy
or load balancer that removes incoming forwarding headers and sets its own.
Otherwise, a client could supply a fake address and avoid per-IP limits.

If the application is exposed directly without a trusted proxy, set the
strategy to `none`. The local installer binds the API to loopback; configure
your production network and proxy deliberately.

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

Flyway applies files from `src/main/resources/db/migration` at startup. Never
edit a migration that has already been applied to a shared database; add a
new migration instead. Back up important data before deploying a migration.
