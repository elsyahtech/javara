# Tutorial: Browser Authentication with Cookies

This tutorial shows the main browser flow when JAVARA uses HttpOnly token
cookies. Cookies reduce the risk of JavaScript reading access and refresh
tokens, but cookie authentication requires CSRF protection and careful CORS
settings.

## 1. Configure cookie transport

In `config/application.yml`, set:

```yaml
app:
  security:
    jwt:
      transport: COOKIE
      cookie-secure: true
      cookie-same-site: Strict
  web:
    cors:
      enabled: true
      allowed-origins:
        - https://app.example.com
      allow-credentials: true
```

Use HTTPS in production. For local HTTP-only experiments, a developer may
temporarily set `cookie-secure: false`; never use that setting for a public
deployment. Cross-site frontends may need `SameSite: None`, HTTPS, and an
exact allowed origin.

Restart JAVARA after changing configuration.

## 2. Allow the frontend origin

Set `allowed-origins` to the exact browser origin, including scheme and
optional port. Do not use `*` with credentials. The CORS allowed headers must
include `X-XSRF-TOKEN`; the setup-generated configuration includes it.

## 3. Get a CSRF token

Before a state-changing request, call:

```http
GET /api/v1/auth/csrf
```

The response includes the token in `data.token` and the required header name
in `data.headerName`. The server also sets the `XSRF-TOKEN` cookie. Allow the
browser to keep that cookie.

## 4. Register or sign in

Call the public registration or login endpoint with JSON and credentials:

```http
POST /api/v1/auth/login
Content-Type: application/json
X-XSRF-TOKEN: <csrf-token>
```

```json
{
  "email": "sam@example.com",
  "password": "your-password"
}
```

For cross-origin requests, the browser client must send credentials (for
example, `credentials: "include"` in Fetch). A successful response sets
HttpOnly access and refresh cookies. The JSON token fields are omitted in
cookie mode, so JavaScript does not read the token values.

## 5. Call protected endpoints

The browser sends the access cookie automatically when the request uses
credentials. For `POST`, `PUT`, `PATCH`, and `DELETE`, also send the current
CSRF token in the `X-XSRF-TOKEN` header.

The CSRF token is not an authentication token. Continue to let the browser
store it in `XSRF-TOKEN` and copy its value to the configured header.

## 6. Refresh and log out

The refresh cookie is scoped to `/api/v1/auth` and is sent to the refresh
endpoint by the browser. Refresh rotates both server-side token state and the
cookies. Do not try to read the HttpOnly refresh cookie in application
JavaScript.

Logout needs the refresh cookie and a CSRF header, but not a valid access
cookie. This lets users log out after the access token expires. The server
clears the token cookies on successful logout.

## Troubleshooting

- **No cookie is stored:** check HTTPS and `cookie-secure`.
- **Browser reports a CORS error:** verify the exact origin,
  `allow-credentials: true`, and the `X-XSRF-TOKEN` allowed header.
- **State-changing request returns `403`:** fetch a fresh CSRF token and send
  it in the expected header.
- **Cross-site cookies are missing:** use HTTPS and review `SameSite`; browsers
  usually require `SameSite=None` for cross-site cookies.

If you do not need browser cookies, use the
[Bearer workflow tutorial](first-api-workflow.md).
