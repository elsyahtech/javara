# Service-to-service authentication

JAVARA provides OAuth 2.0 Client Credentials for backend services. Service
clients are separate from user accounts and can receive only configured
scopes; service tokens do not contain user `ADMIN` or `USER` roles.

## Configure the module

Service authentication is enabled by default and can be disabled independently:

```properties
app.security.service-auth.enabled=true
app.security.service-auth.issuer=https://api.example.com
app.security.service-auth.access-token-ttl=5m
app.security.service-auth.allowed-scopes[0]=service.read
```

Set `issuer` to the stable, externally visible base URL for this authorization
server. It must match the URL resource servers expect in the JWT `iss` claim.
The default issuer is `http://localhost:8080` for local development. The
default token lifetime is five minutes (maximum one hour), and the default
allowlist contains only `service.read`. Configure only the scopes required by
your services. Flyway creates the PostgreSQL-backed OAuth client and
authorization tables.

When `app.security.service-auth.enabled=false`, the authorization endpoint and
the administrative service-client API are not registered. User cookie/Bearer
authentication remains available.

## Provision and manage credentials

An authenticated user with the `ADMIN` role can provision a client:

```http
POST /api/v1/service-clients
Authorization: Bearer <admin-user-access-token>
Content-Type: application/json

{"name":"inventory sync","scopes":["service.read"]}
```

The response contains a generated `clientId` and `clientSecret`. The secret is
returned only during provisioning or rotation; persist it in a secret manager
immediately. PostgreSQL stores only a BCrypt-encoded secret. Provisioning
scopes not present in `allowed-scopes` is rejected.

Rotate a secret with
`POST /api/v1/service-clients/{clientId}/rotate`. The previous secret stops
authenticating immediately, and the new secret is shown once. Revoke a client
with `DELETE /api/v1/service-clients/{clientId}`. Revocation disables further
token grants and causes this application to reject that client's outstanding
JWTs. A client cannot be re-enabled; provision a new client instead.

All management operations require an administrator user token. Never expose
client secrets in browser applications, URLs, source control, or logs.

## Request an access token

Clients authenticate with HTTP Basic (`client_secret_basic`) at the standard
OAuth token endpoint:

```sh
curl --user "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=service.read' \
  https://api.example.com/oauth2/token
```

Only the `client_credentials` grant is registered. Refresh tokens and
interactive/authorization-code grants are not issued. The response follows
OAuth 2.0 token-response conventions and includes `access_token`, `token_type`,
`expires_in`, and the granted `scope`.

Use the token as a Bearer token when calling protected APIs. Resource-server
authentication maps each granted scope to `SCOPE_<scope>` (for example,
`SCOPE_service.read`) for Spring authorization rules. Service principals are
distinguished by the `principal_type=service` and `client_id` JWT claims; they
receive no user roles. Resource APIs should authorize the narrowest required
scope rather than treating an authenticated service as an administrator.

Access tokens are signed using the configured JWT signing key and are
self-contained. In this application, client revocation is checked against the
PostgreSQL client record when service tokens are validated. Independent
resource servers that do not perform this active-client check can continue to
accept an already-issued token until its short expiration time.
