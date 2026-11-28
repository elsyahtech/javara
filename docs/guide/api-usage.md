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

Refresh-token rotation is enabled by default and can be selected in
`make security-setup`. When enabled, every successful refresh returns a new
refresh token; save it and discard the old one. When rotation is disabled,
refresh returns the same refresh token and issues only a new access token.
User access tokens are bound to a backend-validated session. Logout, password
changes, account disablement, and account deletion revoke that session or the
user's sessions, so an already-issued access token is rejected on its next
protected request.

Logout uses the refresh token to identify the session; the access token is not
required. Use the newest refresh token when possible. A previously rotated
refresh token that JAVARA still has on record also logs out its associated
session, so logout remains effective if a client retained an older token.
Logout is idempotent: repeating it for that session does not restore the
session or its tokens.

The default session policy allows five concurrent sessions per user, rejects a
new login at that limit, expires sessions after 15 minutes without activity,
and sets a maximum session duration of 12 hours from sign-in. Idle expiry and
the maximum session duration can each be configured with `make security-setup`.
The maximum session duration is enabled by default; if disabled, the configured
refresh-token TTL becomes the fixed maximum session lifetime measured from
sign-in. Token rotation does not move this end time. The YAML setting for this
option is `absolute-lifetime-enabled`. Session policy is currently global;
per-tenant tightening is not available until tenant policy support is designed
and implemented. Older access tokens without a session identifier are rejected
after the session-lifecycle migration, so clients must authenticate again.
Successful authenticated requests and refreshes update the server-recorded
last-activity time. This measures request activity, not keyboard or mouse
activity, so background polling or refreshes can keep a session active while
the user is away. Refresh-token TTL is only an upper bound: refresh tokens
cannot outlive their session and are rejected when the session expires from
inactivity, even if the configured refresh-token TTL is 30 days.

### What happens when a token expires?

An access token is the short-lived credential sent with API requests. A
refresh token can replace expired tokens only while its server-side session
remains valid; it does not restart an expired or revoked session.

This separation is intentional: short-lived access tokens limit how long a
stolen access token can be used, while refresh tokens let an active user
continue without entering their password for every new access token. The
server-side session adds separate inactivity and maximum-lifetime limits and
lets JAVARA revoke access before a JWT's own expiry. So an expired access token
does not automatically mean the user has to sign in again.

| Situation | What to expect | What to do |
|---|---|---|
| The access token expires, but the session and refresh token are still valid | A protected request may return `401`; refresh issues a new access token and either a new refresh token (rotation on) or the same refresh token (rotation off). | In `COOKIE` mode, the browser adapter refreshes and retries the request once. In `BEARER` mode, call `POST /api/v1/auth/refresh`; save the returned refresh token only when rotation is enabled, then retry the original request once. |
| The enabled idle timeout is reached | Refresh returns `401` (`Refresh token is invalid or expired`). | Sign in again. By default, the idle timeout is 15 minutes; a successful authenticated request or refresh restarts this idle timer. If refresh was already rejected after the timeout, that session was revoked; changing the setting later cannot restore it. |
| The absolute session lifetime or refresh-token TTL is reached | Refresh returns `401`, even if the user was active recently. | Sign in again. Refreshing does not extend the session's absolute end time; that lifetime is capped at 12 hours. |
| The session was logged out, revoked, or invalidated by a security change | Existing access and refresh tokens cannot continue that session. | Sign in again if the account is still allowed to sign in. |
| The user already has the configured maximum number of active sessions | A new login returns `409`; JAVARA does not automatically evict another session. | Terminate an existing session, then sign in again. The default limit is five sessions per user. |

Example with the default 15-minute access-token lifetime: if idle expiry is
disabled and a user waits more than 15 minutes, a protected request using the
old access token can still return `401` because that access token expired.
Refreshing with the current refresh token can succeed; use the new access
token for the next protected request. The result of the refresh—not the first
`401` alone—shows whether the session can continue.

The `401` responses described above are normal authentication outcomes, not
proof that JAVARA has crashed. If refresh itself returns `401`, stop retrying
with that token and ask the user to sign in again. A `5xx` response or a
network error is a different problem and should be handled as a service or
connectivity failure.

For `BEARER` mode, send the latest refresh token in the request body:

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{"refreshToken":"<latest-refresh-token>"}
```

With rotation enabled, every successful refresh rotates the refresh token.
Never retry with the old token after a successful refresh: reusing a
rotated/revoked token is treated as possible token theft and revokes that
user's active sessions. With rotation disabled, the refresh token remains
unchanged and replay detection is unavailable; protect it from theft and rely
on its fixed expiry and session revocation. In `COOKIE` mode, the browser sends
the HttpOnly refresh cookie; see the
[cookie authentication tutorial](../tutorial/browser-cookie-authentication.md).
When rotation is enabled, custom clients should allow only one refresh request
at a time per session; two simultaneous refreshes using the same token can
look like token reuse. The
optional [browser adapter](../../sdk/browser/README.md) coordinates concurrent
refreshes for browser requests.

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
| `POST /api/v1/auth/refresh` | Public | Issues a new access token; rotates the refresh token when rotation is enabled |
| `POST /api/v1/auth/logout` | Refresh token for the session | Revokes that session and its access tokens; cookie mode also requires a CSRF token |
| `POST /api/v1/auth/password/change` | USER or ADMIN | Requires current password; revokes all sessions and requires signing in again |
| `GET /api/v1/auth/sessions` | Authenticated USER | Lists the user's active sessions |
| `POST /api/v1/auth/sessions/{sessionId}/terminate` | Same USER | Requires current password; terminates the selected session |
| `POST /api/v1/auth/sessions/terminate-all` | Same USER | Requires current password; terminates all sessions |
| `POST /api/v1/admin/users/{userId}/sessions/terminate` | ADMIN | Requires the administrator's current password; terminates the target user's sessions |
| `GET /api/v1/auth/csrf` | Public in cookie mode | Gets a CSRF token; not found in Bearer mode |
| `GET /api/v1/users?page=0&size=20` | `users:read` permission | Lists users; built-in ADMIN has this permission by default |
| `POST /api/v1/users` | `users:manage` permission | Creates a USER; built-in ADMIN has this permission by default |
| `GET /api/v1/users/{id}` | Same user or `users:read` | Gets a user |
| `GET /api/v1/users/findUserByIdWithSelectedColumns/{id}` | Same user or `users:manage` | Gets selected fields |
| `PUT /api/v1/users/{id}` | Same user or `users:manage` | Changes active status only; email and password use dedicated endpoints |
| `DELETE /api/v1/users/{id}` | Same user or `users:manage` | Soft-deletes the account and revokes its sessions |
| `GET /api/v1/users/{userId}/roles` | Same user or `users:read` | Reads the user's assigned roles |
| `PUT /api/v1/users/{userId}/roles` | `roles:manage` permission | Replaces role assignments |
| `GET /api/v1/roles` | `roles:manage` permission | Lists built-in and custom role definitions |
| `GET /api/v1/roles/permissions` | `roles:manage` permission | Lists assignable permission keys |
| `POST /api/v1/roles` | `roles:manage` permission | Creates a custom role |
| `PUT /api/v1/roles/{key}` | `roles:manage` permission | Updates a custom role |
| `DELETE /api/v1/roles/{key}` | `roles:manage` permission | Deletes an unassigned custom role |

“Same user” means the authenticated account ID must match the `{id}` or
`{userId}` in the path. This gives a regular authenticated user self-service
access to their own user record and role list; it does not let them list users
or manage another account. The self-service `PUT` can deactivate the user's
own account, which revokes its sessions; `DELETE` soft-deletes the user's own
account and also revokes its sessions. Use these actions carefully. Email and
password changes have dedicated endpoints with re-authentication/verification
requirements.

Authorization checks permissions, not hardcoded role names. The built-in
ADMIN role receives management permissions by default, but a custom database
role can also receive catalogued permissions. The code defines which
permission protects each endpoint; creating a role cannot invent new
endpoint capabilities.

Role definitions, assignments, and permissions are stored in PostgreSQL and
resolved from the database for each authenticated request. Redis is used by
authentication rate limiting, not for role lookup or authorization. In the
current `SINGLE` tenancy mode, roles are associated with the internal default
organization. `SHARED` multi-organization runtime is not yet supported; see
the [tenant and role security model](../security/assessment/tenant-model.md).

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

An incorrect code on a valid OTP challenge returns the generic `401` challenge
error. The user can retry while the challenge remains valid, until
`max-attempts` is reached (five by default); failed login MFA attempts also
count toward the configured suspicious-authentication threshold and may
trigger its configured action. A correct code before expiry or the attempt
limit completes authentication and returns tokens.

An authenticated user can inspect policy and enrolled factors with
`GET /api/v1/auth/mfa`. Email enrollment starts at
`POST /api/v1/auth/mfa/email/enroll` with `currentPassword`, then completes at
`POST /api/v1/auth/mfa/email/enroll/verify` with `challengeId` and `code`.
An enrollment challenge has `action: "ENROLL"` and must be completed through
the enrollment verification endpoint. `POST /api/v1/auth/mfa/login/otp/verify`
is for login challenges (`action: "VERIFY"`) and required enrollment during
login. Sending an enrollment challenge to the login endpoint returns the
generic `401` challenge error;
that purpose mismatch does not check the OTP or consume the enrollment
challenge. Submitting a wrong code to the correct enrollment endpoint does
count as a failed attempt; after the configured `max-attempts` (five by
default), the challenge is consumed and a new enrollment challenge is needed.
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
| `401` | Missing/invalid authentication, invalid credentials, or an expired/revoked session or refresh token |
| `403` | Authenticated but not allowed to perform this action |
| `404` | Requested user/resource not found |
| `409` | Duplicate active email, concurrent update conflict, or maximum active sessions reached |
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
