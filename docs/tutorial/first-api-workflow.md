# Tutorial: Complete a First API Workflow

This tutorial uses `curl` to connect a client to a local JAVARA server. It
uses the default `BEARER` token transport.

## 1. Start JAVARA

From the repository root:

```bash
make install
make run
```

In another terminal, confirm the server responds:

```bash
curl http://localhost:8080/
```

## 2. Register a USER

Registration is public and creates a `USER`, not an ADMIN:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"sam@example.com","password":"local-demo-123"}'
```

The success response is wrapped in `data`. Copy the `id` from
`data.id`; you will use it in the next step. No demo user is inserted by
database migrations.

## 3. Sign in

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"sam@example.com","password":"local-demo-123"}'
```

The response contains `data.accessToken` and `data.refreshToken`. Copy these
values from the response and set them in your terminal:

```bash
USER_ID="<copy-data.id-from-registration>"
ACCESS_TOKEN="<copy-data.accessToken-from-login>"
REFRESH_TOKEN="<copy-data.refreshToken-from-login>"
```

These placeholders are examples. Replace them with values from your local
responses.

## 4. Call a protected endpoint

A USER can read their own ID-based resource:

```bash
curl -i "http://localhost:8080/api/v1/users/$USER_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

The response should contain the user ID and email. A USER cannot list all
accounts; those routes are reserved for ADMIN.

## 5. Refresh the session

Refresh rotates the refresh token. The response returns a new access token
and a new refresh token:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Replace **both** shell variables with the new `data.accessToken` and
`data.refreshToken` values. Do not keep using the old refresh token.

## 6. Log out

Logout needs only the current refresh token. The access token can be expired:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

This revokes the refresh token. An already-issued access token remains valid
until its short expiry. If the account password changes, active refresh
tokens are revoked as well.

## Common outcomes

- `400`: invalid email or password format.
- `401`: invalid credentials, token, or missing authentication.
- `403`: the account is signed in but lacks permission.
- `404`: the user ID does not exist or is not visible.
- `409`: the email is already in use.
- `429`: the endpoint rate limit was reached; wait for `Retry-After`.
- `503`: Redis, which stores rate-limit counters, is unavailable.

For a browser application, prefer the
[cookie authentication tutorial](browser-cookie-authentication.md) rather
than storing bearer tokens in browser storage.
