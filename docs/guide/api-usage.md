# API and Access Rules

The API base path is `/api/v1`. The root `GET /` endpoint is a simple
availability response; it is not a full health or readiness check.

## Authentication

In `BEARER` mode, sign in at `POST /api/v1/auth/login`. If MFA is not required
for the account, the response contains `data.accessToken` and
`data.refreshToken`. When MFA is enabled for the account, login instead returns
`202 Accepted` with `data.mfaChallenge`; no token or cookie is issued until
the challenge is completed. Send the resulting access token on protected
requests:

```http
Authorization: Bearer <access-token>
```

Public registration creates an active `USER`, never an ADMIN. It returns the
same generic `202 Accepted` response whether the address is new or already
registered; the response does not return a user record or disclose account
existence. For a new address, verification instructions are sent by email.
New public registrations must verify their email before they can sign in; the
initial ADMIN and accounts that existed before the email-verification migration
remain verified. The initial ADMIN is created from bootstrap configuration. Do
not expose bootstrap credentials to clients.

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
| `POST /api/v1/auth/register` | Public | Creates an active, unverified USER when eligible; always returns the same generic `202` response to avoid account enumeration |
| `GET /api/v1/auth/email/verify?token=...` | Public | Displays a confirmation form; does not consume the token |
| `POST /api/v1/auth/email/verify` | Public | Consumes a single-use verification token |
| `POST /api/v1/auth/email/verification/resend` | Public | Requests another registration verification email |
| `POST /api/v1/auth/password/reset/request` | Public | User requests a password-reset link; response is generic to prevent account enumeration |
| `GET /api/v1/auth/password/reset?token=...` | Public | Displays a password-reset form; does not consume the token |
| `POST /api/v1/auth/password/reset` | Public | Accepts a URL-encoded one-time token and new password |
| `POST /api/v1/auth/email/change` | Authenticated USER or ADMIN | Requires the current password; confirms the new address before changing it |
| `POST /api/v1/admin/users/{userId}/email/change` | ADMIN | Requires the administrator's current password and confirmation of the new address |
| `POST /api/v1/admin/users/{userId}/security/force-password-reset` | ADMIN | Emergency action: replaces the password with an undisclosed random secret, revokes sessions, locks sign-in, and emails an admin-authored notice |
| `POST /api/v1/admin/users/{userId}/password/recovery/authorize` | ADMIN | After out-of-band support verification, sends a one-time link for the user to choose a password |
| `POST /api/v1/auth/login` | Public | Returns tokens or an MFA challenge; unverified accounts receive the generic invalid-credentials response |
| `POST /api/v1/auth/mfa/login/otp/verify` | Public MFA challenge | Verifies a one-time code from a configured provider; issues tokens only after successful verification |
| `POST /api/v1/auth/mfa/login/passkey/verify` | Public MFA challenge | Verifies a WebAuthn assertion; issues tokens only after successful verification |
| `POST /api/v1/auth/mfa/login/passkey/enroll` | Public required-enrollment challenge | Completes required passkey enrollment before issuing tokens |
| `GET /api/v1/auth/mfa` | USER or ADMIN | Returns deployment policy, enrolled methods, and preferred method |
| `POST /api/v1/auth/mfa/email/enroll` | USER or ADMIN | Requires the current password and sends an email OTP |
| `POST /api/v1/auth/mfa/email/enroll/verify` | USER or ADMIN | Verifies the enrollment OTP; enabling the factor revokes active sessions |
| `POST /api/v1/auth/mfa/passkeys/enroll/options` | USER or ADMIN | Requires the current password and returns WebAuthn creation options |
| `POST /api/v1/auth/mfa/passkeys/enroll` | USER or ADMIN | Completes passkey enrollment; enabling the factor revokes active sessions |
| `POST /api/v1/auth/mfa/preference` | USER or ADMIN | Selects an enrolled, deployment-allowed preferred factor; requires current password |
| `POST /api/v1/auth/mfa/disable` | USER or ADMIN | Disables an enrolled factor; requires current password |
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
| `PUT /api/v1/users/{id}` | Same USER or ADMIN | Updates optional active status; cannot change email or password |
| `DELETE /api/v1/users/{id}` | Same USER or ADMIN | Soft-deletes the user |

“Same USER” means the authenticated account ID must match the `{id}` in the
path. ADMIN can manage other users. List and create operations are
ADMIN-only.

### MFA login and enrollment

The login JSON may include an optional `mfaMethod` (`EMAIL_OTP` or
`WEBAUTHN`) to select an enrolled factor allowed by deployment policy:

```json
{
  "email": "user@example.com",
  "password": "your-password",
  "mfaMethod": "EMAIL_OTP"
}
```

Without that field, JAVARA uses the account's preferred factor. A challenge
response has no access token, refresh token, or authentication cookie:

```json
{
  "httpCode": 202,
  "success": true,
  "data": {
    "mfaChallenge": {
      "challengeId": "challenge-uuid",
      "method": "EMAIL_OTP",
      "availableMethods": ["EMAIL_OTP", "WEBAUTHN"],
      "action": "VERIFY",
      "expiresAt": "2026-11-26T16:00:00Z"
    }
  }
}
```

For an OTP, submit `challengeId` and the eight-digit code to
`POST /api/v1/auth/mfa/login/otp/verify`. For a passkey, send `challengeId`
and the browser-generated assertion JSON to
`POST /api/v1/auth/mfa/login/passkey/verify`. Under required policy, a new
account first enrolls the configured default factor: complete the email code
challenge through the email verification endpoint or submit the WebAuthn
registration credential to `/api/v1/auth/mfa/login/passkey/enroll`. The API
issues tokens only after verification or enrollment succeeds.

An authenticated user can inspect policy and enrolled factors with
`GET /api/v1/auth/mfa`. Email enrollment starts at
`POST /api/v1/auth/mfa/email/enroll` with `currentPassword`, then completes at
`POST /api/v1/auth/mfa/email/enroll/verify` with `challengeId` and `code`.
Passkey enrollment starts at
`POST /api/v1/auth/mfa/passkeys/enroll/options` with `currentPassword`; submit
the returned creation options and browser-generated credential to
`POST /api/v1/auth/mfa/passkeys/enroll`. Use `/mfa/preference` to choose an
enrolled allowed factor and `/mfa/disable` to remove one; both require the
current password. Enabling or changing factors revokes active sessions.
Required policy prevents disabling the last factor.

The allowed-method list is deployment-wide; there is no tenant-specific MFA
policy. SMS and WhatsApp providers and phone-number enrollment are not
implemented. Email OTP is not phishing-resistant, and MFA is not yet a
step-up control for sensitive operations.

## Request and response format

User creation expects JSON:

```json
{
  "email": "person@example.com",
  "password": "at-least-8-characters"
}
```

Passwords must be 8–72 characters. Public registration creates an active USER
whose email must be verified before login. Verification links expire after the
configured duration (24 hours by default) and can only be used once. The
resend endpoint responds with `202 Accepted` whether or not an account needs
verification. User-management `PUT` accepts optional `isActive`; email changes
must use the dedicated confirmation flow, and including a `password` is
rejected. Self-service password changes require an authenticated USER or ADMIN
session and the current password:

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

Forgotten-password recovery is initiated by the user:

```http
POST /api/v1/auth/password/reset/request
Content-Type: application/json
```

```json
{
  "email": "person@example.com"
}
```

The response is always `202 Accepted` and does not reveal whether the address
has an account. Eligible users receive a single-use link at their verified
address. The user chooses the new password; successful completion applies the
password policy and revokes all active sessions.

For an emergency such as a suspected password compromise, an administrator can
invalidate the credential immediately. This action requires the administrator's
current password, an incident reason, and a plain-text notice for the user:

```http
POST /api/v1/admin/users/{userId}/security/force-password-reset
Content-Type: application/json
Authorization: Bearer <admin-access-token>
```

```json
{
  "currentPassword": "admin-current-secret",
  "reason": "Suspected credential compromise",
  "notificationMessage": "We detected activity that may put your account at risk. Contact support using the verified phone number on file."
}
```

JAVARA replaces the credential with a randomly generated secret that is never
shown to the administrator or user, revokes all sessions, blocks sign-in, and
sends a security notice to the user's verified address. The notice includes the
administrator-authored plain-text message, but never includes the generated
secret or a reset link. The reset-required state also prevents the user from
bypassing support by requesting a normal reset.

After support verifies the user's identity through a separate trusted channel,
an administrator records the support case reference and authorizes recovery:

```http
POST /api/v1/admin/users/{userId}/password/recovery/authorize
Content-Type: application/json
Authorization: Bearer <admin-access-token>
```

```json
{
  "currentPassword": "admin-current-secret",
  "supportCaseReference": "SUP-1234"
}
```

This sends a one-time reset link to the verified address. It does not unlock the
account by itself; the user must choose a password through the link. Links
expire after one hour. Reset-token values are stored as hashes, are never
returned by the API, and must be redacted from query-string logs and monitoring.
The password that was invalidated by the emergency reset is retained as a
one-way hash and rejected by later password changes and resets.
If a password is rejected, the reset page explains that it was invalidated for
security reasons and keeps the recovery form available so the user can enter a
different password without requesting a new link.
The reset page uses `no-store` and `no-referrer` headers; email-link scanners
only display the form and do not consume the token. Emergency actions,
support authorizations, and reset completions are audited without storing
passwords or reset-token values. If an authorization link expires or SMTP
delivery fails, support must verify the case again and issue a new authorization.
If the user's current email is unverified, the emergency lock still applies,
but no email is sent; support must restore a verified contact channel before
authorizing recovery.

An incorrect current password returns `401 Unauthorized`; reusing the current
password or selecting a password in the configured common/breached-password
datasets returns `400 Bad Request`. The API uses the same generic message for
both dataset matches and never logs the submitted password.
Validation errors identify the affected field and a safe reason; rejected
values, including passwords, are not included in API responses or application
logs.

The verification `GET` only renders a confirmation form; the `POST` consumes
the token so link scanners do not verify accounts merely by following links.
The verification `POST` is exempt from CSRF because possession of its
single-use token is the proof of intent. Email-change requests leave the
current address active until the new address is confirmed; completing a change
revokes the user's active sessions. Password changes, completed email changes,
and new sessions send security notifications after the database transaction
commits.

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
