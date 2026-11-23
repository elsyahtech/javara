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
one. Logout requires only the current refresh token; it still works after the
access token expires. Changing a password revokes that user's active refresh
tokens. An already-issued access token is still accepted until its expiry.

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
| `GET /api/v1/auth/csrf` | Public in cookie mode | Gets a CSRF token; not found in Bearer mode |
| `GET /api/v1/users?page=0&size=20` | ADMIN | Returns a user page with total count |
| `POST /api/v1/users` | ADMIN | Creates a USER |
| `GET /api/v1/users/{id}` | Same USER or ADMIN | Gets a user |
| `GET /api/v1/users/findUserByIdWithSelectedColumns/{id}` | Same USER or ADMIN | Gets selected fields |
| `PUT /api/v1/users/{id}` | Same USER or ADMIN | Replaces the mutable fields in the request |
| `DELETE /api/v1/users/{id}` | Same USER or ADMIN | Soft-deletes the user |

“Same USER” means the authenticated account ID must match the `{id}` in the
path. ADMIN can manage other users. List and create operations are
ADMIN-only.

## Request and response format

User creation and update expect JSON:

```json
{
  "email": "person@example.com",
  "password": "at-least-8-characters"
}
```

The password is required and must be 8–72 characters. On update,
`isActive` is optional. Public registration always creates an active USER.
The user-management `PUT` endpoint requires a password as part of the full
request; it is not a partial-update endpoint.

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
