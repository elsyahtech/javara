# JAVARA Browser Auth Adapter

This optional adapter provides the cookie-authentication flow for browser
applications. It uses `HttpOnly` cookies and never reads or exposes access or
refresh tokens to JavaScript.

```js
import { JavaraAuthClient } from "@javara/browser-auth";

const auth = new JavaraAuthClient({ baseUrl: "https://api.example.com" });

const login = await auth.login({ email, password });
if (!login.ok) {
  // Handle the API response using the application's normal error UI.
}

const response = await auth.request("/api/v1/users");
```

For unsafe requests the adapter gets a CSRF token, sends it in the server-
provided header, and includes cookies. If a protected request returns `401`,
it refreshes once and retries that request once. Concurrent refreshes from
instances in the same page are coalesced; browsers that support the Web Locks
API also serialize refreshes across tabs. If a refresh returns `401`, the
original `401` is returned so the application can require sign-in again.

Serve the application and API over HTTPS in production. For cross-origin
browser deployments, configure an explicit CORS origin, credentials, and
`SameSite=None; Secure` cookies on the API. Do not mix this adapter with
Bearer-token handling in browser storage.

This is the browser-specific adapter, not a platform restriction on JAVARA.
Native and backend clients can use the documented HTTP endpoints; service
clients should use OAuth 2.0 Client Credentials instead of user cookies.

Run its dependency-free tests with:

```bash
npm test --prefix sdk/browser
```
