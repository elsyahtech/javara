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

The generated local configuration binds the API to loopback and does not
trust forwarded headers:

```yaml
server:
  forward-headers-strategy: none
```

With `none`, rate limits use the address of the direct network peer. This is
the safe default for a directly exposed application. If a reverse proxy is in
front, requests will share the proxy's address until proxy handling is
explicitly enabled.

Set the following only when the application is reachable exclusively through
a trusted reverse proxy or load balancer:

```yaml
server:
  forward-headers-strategy: framework
```

Equivalent environment variable:

```text
SERVER_FORWARD_HEADERS_STRATEGY=framework
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

Flyway applies files from `src/main/resources/db/migration` at startup. Never
edit a migration that has already been applied to a shared database; add a
new migration instead. Back up important data before deploying a migration.
