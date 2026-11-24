# API and Access Rules

The API base path is `/api/v1`. The root `GET /` endpoint is a simple
availability response; it is not a full health or readiness check.

## Authentication

In `BEARER` mode, sign in at `POST /api/v1/auth/login`. The response contains
`data.accessToken` and `data.refreshToken`. Send the access token on protected
requests:

```http
Authorization: Bearer <access-token>
```

Registration creates a `USER`, never an ADMIN. The initial ADMIN is created
from bootstrap configuration. Do not expose bootstrap credentials to clients.

Refresh rotates the refresh token. Keep the newest token and discard the old
one. User access tokens are bound to a backend-validated session. Logout,
password changes, account disablement, and account deletion revoke that session
or the user's sessions, so an already-issued access token is rejected on its
next protected request.

The default session policy allows five concurrent sessions per user, rejects a
new login at that limit, expires sessions after 15 minutes without activity,
and enforces a 12-hour absolute lifetime. Session policy is currently global;
per-tenant tightening is not available until tenant policy support is designed
and implemented. Older access tokens without a session identifier are rejected
after the session-lifecycle migration, so clients must authenticate again.

Users can list their active sessions with `GET /api/v1/auth/sessions`. The
session termination endpoints require the user's current password; terminating
all sessions also invalidates the session used to make the request.
Session termination endpoints are limited to five requests per 15 minutes per
client address by default. When deployed behind a proxy, configure trusted
client-address handling in the servlet container so unrelated users are not
grouped under the proxy address; do not trust arbitrary forwarded headers. The
policy is configurable under `app.middleware.rate-limit.session-reauth`.

For browser cookies and CSRF, see the
[cookie authentication tutorial](../tutorial/browser-cookie-authentication.md).

## Endpoint overview

| Method and path | Access | Notes |
|---|---|---|
| `GET /` | Public | Simple welcome response |
| `POST /api/v1/auth/register` | Public | Creates an active USER |
| `POST /api/v1/auth/login` | Public | Returns tokens or sets cookies |
| `POST /api/v1/auth/refresh` | Public | Rotates a refresh token |
| `POST /api/v1/auth/logout` | Current refresh token | Revokes that session; cookie mode also requires a CSRF token |
| `POST /api/v1/auth/password/change` | USER or ADMIN | Requires current password; revokes all sessions and requires signing in again |
| `GET /api/v1/auth/sessions` | Authenticated USER | Lists the user's active sessions |
| `POST /api/v1/auth/sessions/{sessionId}/terminate` | Same USER | Requires current password; terminates the selected session |
| `POST /api/v1/auth/sessions/terminate-all` | Same USER | Requires current password; terminates all sessions |
| `POST /api/v1/admin/users/{userId}/sessions/terminate` | ADMIN | Requires the administrator's current password; terminates the target user's sessions |
| `GET /api/v1/auth/csrf` | Public in cookie mode | Gets a CSRF token; not found in Bearer mode |
| `GET /api/v1/users?page=0&size=20` | ADMIN | Returns a user page with total count |
| `POST /api/v1/users` | ADMIN | Creates a USER |
| `GET /api/v1/users/{id}` | Same USER or ADMIN | Gets a user |
| `GET /api/v1/users/findUserByIdWithSelectedColumns/{id}` | Same USER or ADMIN | Gets selected fields |
| `PUT /api/v1/users/{id}` | Same USER or ADMIN | Updates email and optional active status; cannot change a password |
| `DELETE /api/v1/users/{id}` | Same USER or ADMIN | Soft-deletes the user |

“Same USER” means the authenticated account ID must match the `{id}` in the
path. ADMIN can manage other users. List and create operations are
ADMIN-only.

## Request and response format

User creation expects JSON:

```json
{
  "email": "person@example.com",
  "password": "at-least-8-characters"
}
```

Passwords must be 8–72 characters. Public registration always creates an
active USER. The user-management `PUT` endpoint accepts `email` and optional
`isActive`; including a `password` is rejected. Self-service password changes
require an authenticated USER or ADMIN session and the current password:

```http
POST /api/v1/auth/password/change
Content-Type: application/json
Authorization: Bearer <access-token>
```

```json
{
  "currentPassword": "current-secret",
  "newPassword": "new-secret-at-least-8-characters"
}
```

After a successful change, all sessions are revoked, including the session
that submitted the request. Sign in again using the new password. Cookie
transport also clears the access and refresh cookies. The endpoint shares the
default re-authentication rate limit of five requests per 15 minutes per
client address.

An incorrect current password returns `401 Unauthorized`; reusing the current
password or selecting a password in the configured common/breached-password
datasets returns `400 Bad Request`. The API uses the same generic message for
both dataset matches and never logs the submitted password.
Validation errors identify the affected field and a safe reason; rejected
values, including passwords, are not included in API responses or application
logs.

Responses use an envelope. Successful responses contain fields such as
`httpCode`, `message`, `success`, and `data`. Errors contain `httpCode`,
`success`, and `error`. The users collection returns `200 OK` with an empty
`data` array and `totalElements: 0` when no users exist.

## Pagination

`GET /api/v1/users` returns a page containing only the selected public user
fields. It accepts:

- `page`: zero-based page index, default `0`
- `size`: page size, default `20`, allowed range `1`–`100`
- Results use `createdAt` descending and `id` ascending as a stable order.

Requests with a size outside `1`–`100` return `400 Bad Request`. Results use
a stable order so adjacent pages are less likely to repeat or skip records
when creation timestamps match. The response includes `page`, `size`,
`totalElements`, `totalPages`, `hasNext`, and `hasPrevious` alongside `data`.
An out-of-range page is still a successful empty page.

Example response:

```json
{
  "httpCode": 200,
  "message": "Users retrieved successfully",
  "success": true,
  "data": [
    {
      "id": "0c472662-aeac-4f82-b7e1-703f94c79641",
      "email": "person@example.com",
      "isActive": true,
      "createdAt": "2026-11-23T12:00:00",
      "updatedAt": null,
      "deletedAt": null
    }
  ],
  "count": 1,
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false,
  "hasPrevious": false
}
```

## Common status codes

| Status | Meaning |
|---|---|
| `200` | Request succeeded |
| `201` | User created |
| `400` | Invalid request, validation error, or unsupported page size |
| `401` | Missing/invalid authentication or invalid credentials |
| `403` | Authenticated but not allowed to perform this action |
| `404` | Requested user/resource not found |
| `409` | Duplicate active email or concurrent update conflict |
| `429` | Endpoint rate limit reached; check `Retry-After` |
| `503` | Redis rate-limit service is unavailable |

## Data behavior

- Passwords are stored as hashes; never send or log passwords outside the
  required request.
- Refresh tokens are stored in PostgreSQL as SHA-256 hashes.
- User deletion is soft deletion. The database row remains, but normal reads
  hide it and its email can be reused.
- Flyway migrations manage the schema. Do not edit an already-applied
  migration.

See the root [README API reference](../../README.md#api-reference) for
additional response examples.
