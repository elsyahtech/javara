# Authentication and Session Management Review

## Status and scope

This is the first repository evidence review for ASVS v5.0.0 chapters V6
(Authentication) and V7 (Session Management). It is not an independent audit,
penetration test, certification, or production-readiness claim.

- Source baseline: `aacdb7858552fe2cfd35eb40ae04808d0e33c120`, as pinned in
  [assessment scope](scope.md).
- Review checkout: `0a1c7108a4b5ba74cbb2c99eb36d943464b75381`; changes from the
  source baseline at that point were assessment documentation only.
- Requirements reviewed: 21 marked `Fail`; the other 324 remain `Not assessed`.
- No requirement is marked `Pass` or excluded.

The findings below are confirmed from the repository implementation and
documentation. They are remediation items, not approved exceptions. The
official requirement text is available in the
[OWASP ASVS v5.0.0 V6 source](https://github.com/OWASP/ASVS/blob/a2ce628f8324a294202c09cff9b0c19f13cf0d68/5.0/en/0x15-V6-Authentication.md)
and [V7 source](https://github.com/OWASP/ASVS/blob/a2ce628f8324a294202c09cff9b0c19f13cf0d68/5.0/en/0x16-V7-Session-Management.md).

## Verification performed

The following focused unit and integration test command completed successfully:

```text
bash scripts/maven.sh -Dtest=AuthControllerTests,AuthServiceTests,AccessTokenServiceTests,SecurityAuthorizationIntegrationTests,CookieSecurityIntegrationTests,ServiceClientCredentialsIntegrationTests,AuthRateLimiterTests,AuthRateLimitInterceptorTests test
```

Result: 32 tests, 0 failures, 0 errors, 0 skipped. Passing tests demonstrate
the behavior they exercise; they do not supply missing controls such as MFA,
password recovery, or access-token revocation.

The review also inspected the password request/hash path, authentication and
user controllers, token lifecycle, JWT validation, and the production/API
guides. No production or staging environment was available or assessed.

## Remediation progress in the current worktree

The assessment baseline above is historical. The following remediation is
present in the current, uncommitted worktree and has not yet been independently
re-assessed. The original matrix findings remain baseline findings; no
requirement is being claimed as `Pass`.

- User access tokens now carry a session identifier and are checked against
  active session state on protected requests. Logout, password changes, account
  disablement/deletion, and explicit session termination revoke access through
  that server-side check.
- Refresh-token records now preserve session start and last-activity times.
  Defaults are 15 minutes idle, 12 hours absolute, and five concurrent sessions;
  another login is rejected at the limit. The current policy is global, not
  tenant-specific.
- Users can list and terminate their sessions; administrators can terminate a
  target user's sessions. These termination actions require the acting user's
  current password and are rate-limited to five requests per 15 minutes per
  client address by default.
- The API guide documents these defaults, endpoint behavior, migration impact,
  and deployment caveats.

The focused regression command completed successfully with 46 tests,
0 failures, and 0 errors:

```text
bash scripts/maven.sh -Dtest=AuthControllerTests,AuthServiceTests,SessionTokenValidatorTests,AccessTokenServiceTests,SecurityAuthorizationIntegrationTests,CookieSecurityIntegrationTests,ServiceClientCredentialsIntegrationTests,AuthRateLimiterTests,AuthRateLimitInterceptorTests,AuthRateLimitWebMvcConfigurationTests test
```

This verifies only the tested repository behavior. Database-write cost on every
authenticated user request has not been load-tested. Token/timeout behavior
still needs deployment-level verification. Email changes still lack the
required re-authentication and verification flow, and MFA, password recovery,
breached-password checks, and registration anti-enumeration remain open.

## Confirmed findings

### V6 - Authentication

| Finding | Requirement | Evidence and gap | Remediation target |
|---|---|---|---|
| AUTH-01 | V6.1.2, V6.2.11 | No context-specific password-word list is documented or enforced. The password request validates presence, length, and email format; the password service only encodes and verifies hashes. See [`UserRequest`](../../../src/main/java/com/elsyah/tech/javara/request/user/UserRequest.java), [`PasswordHashService`](../../../src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java). | Define a configurable JAVARA/customer context-word policy and enforce it during registration and password changes. |
| AUTH-02 | V6.2.3 | The self-service `PUT /api/v1/users/{id}` accepts the new password but has no current-password field or re-authentication step. The route permits the authenticated user to update their own record. See [`UserController`](../../../src/main/java/com/elsyah/tech/javara/controller/user/UserController.java), [`UserRequest`](../../../src/main/java/com/elsyah/tech/javara/request/user/UserRequest.java), and [`UserService.updateUser`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java). | Add a dedicated password-change flow requiring the current password or equivalent full re-authentication. |
| AUTH-03 | V6.2.4 | Registration and password updates proceed to password hashing without checking a top-3000 disallowed-password set. See [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java) and [`PasswordHashService`](../../../src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java). | Check candidate passwords against a maintained set that matches the configured password policy. |
| AUTH-04 | V6.2.12 | No breached-password check exists in the password creation or update path. | Add a privacy-preserving breached-password check and verify it is applied to registration and password changes. |
| AUTH-05 | V6.3.3 | The production guide explicitly lists multi-factor authentication as unavailable. Level 3 requires a phishing-resistant hardware-based factor unless a fully documented rationale and comprehensive mitigations are accepted. See [production boundaries](../../guide/production-and-adoption.md). | Implement and verify a hardware-based, phishing-resistant factor (for example, an appropriate WebAuthn/passkey design); do not claim L3 before verification. |
| AUTH-06 | V6.3.5 | No mechanism was found to detect and notify users of suspicious successful or unsuccessful authentication attempts. The source has no notification delivery path. | Define detection signals, notification delivery, abuse controls, and tests for suspicious-attempt cases. |
| AUTH-07 | V6.3.7 | User email/password changes have no notification flow. The production guide lists email capabilities as unavailable. See [production boundaries](../../guide/production-and-adoption.md) and [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java). | Notify the user through a verified channel after authentication-detail changes, with delivery and failure handling tested. |
| AUTH-08 | V6.3.8 | Login uses a generic invalid-credentials message, but public registration distinguishes an existing email with HTTP 409 and `"Email already exists"` from successful registration. The public route is enabled. See [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java), [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java), and [`ApiSecurityConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/ApiSecurityConfiguration.java). | Make registration responses resistant to account enumeration, including response code, body, and timing behavior. |
| AUTH-09 | V6.4.3 | Password recovery is explicitly unavailable and no forgotten-password endpoint or recovery process is implemented. See [production boundaries](../../guide/production-and-adoption.md) and [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java). | Implement a secure recovery process that preserves all enabled authentication factors and prevents account enumeration. |
| AUTH-10 | V6.4.6 | The admin-capable user update endpoint accepts a chosen password in `UserRequest`; there is no separate reset-initiation flow that prevents administrators from knowing or choosing the user's password. See [`UserController`](../../../src/main/java/com/elsyah/tech/javara/controller/user/UserController.java) and [`UserRequest`](../../../src/main/java/com/elsyah/tech/javara/request/user/UserRequest.java). | Replace direct admin password setting with a reset initiation flow that gives the user control of the new secret. |

### V7 - Session Management

| Finding | Requirement | Evidence and gap | Remediation target |
|---|---|---|---|
| SES-01 | V7.1.1 | Documentation does not define an inactivity timeout and absolute maximum session lifetime together with risk analysis and justification for any NIST re-authentication deviations. Token TTL configuration alone is not that documented decision. | Document session timeout semantics, risk rationale, and re-authentication policy; align implementation and tests. |
| SES-02 | V7.1.2 | No documented maximum number of concurrent sessions or behavior when that limit is reached was found. Each successful login can create a refresh-token record. See [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java). | Decide, document, enforce, and test the concurrent-session policy. |
| SES-03 | V7.3.2 | Refresh rotation sets each replacement token's expiry to `now + refreshTokenTtl`; the implementation has no absolute session start/maximum lifetime to stop repeated refreshes extending the session. See [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java). | Preserve the original session start and enforce a maximum lifetime requiring re-authentication. |
| SES-04 | V7.4.1 | Logout revokes the reference refresh token, but access JWTs are validated statelessly and remain usable until expiry. The API guide explicitly documents that an issued access token is still accepted until expiry after a password change. See [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java), [`JwtTokenConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/JwtTokenConfiguration.java), and [API token lifecycle](../../guide/api-usage.md). | Make termination effective for access tokens too, such as with a revocation/version check or another verified server-side strategy. |
| SES-05 | V7.4.2 | Disabling or soft-deleting a user does not revoke active refresh tokens in `UserService`; refresh rejects inactive/missing users later, but JWT validation does not check current user status, so issued access tokens remain usable until expiry. See [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java), [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java), and [`JwtTokenConfiguration`](../../../src/main/java/com/elsyah/tech/javara/middleware/security/JwtTokenConfiguration.java). | Revoke all session types on disable/delete and verify no protected request accepts a pre-existing token afterward. |
| SES-06 | V7.4.3 | Password changes revoke refresh tokens, but do not invalidate already-issued access JWTs. Consequently, other active sessions are not fully terminated after a factor change. See [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java) and [API token lifecycle](../../guide/api-usage.md). | Provide and test an effective option to terminate all other sessions after changing an authentication factor. |
| SES-07 | V7.4.5 | No administrator endpoint or service operation to terminate another user's active sessions was found; current auth routes only cover login, refresh, logout, and CSRF. See [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java). | Add audited administrator controls to terminate one user's sessions or all sessions, with authorization tests. |
| SES-08 | V7.5.1 | An authenticated user can change their own email through `PUT /api/v1/users/{id}` without providing the current password or completing full re-authentication. See [`UserController`](../../../src/main/java/com/elsyah/tech/javara/controller/user/UserController.java) and [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java). | Require full re-authentication before changing email or other authentication/recovery attributes. |
| SES-09 | V7.5.2 | No endpoint allows a user to view or terminate any/all active sessions; logout only revokes the presented refresh token. See [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java) and [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java). | Add a user session-management flow with re-authentication before terminating sessions. |
| SES-10 | V7.5.3 | Sensitive account changes are authorized by the existing access token alone; no step-up or secondary verification is present. See [`UserController`](../../../src/main/java/com/elsyah/tech/javara/controller/user/UserController.java). | Identify highly sensitive operations and require a verified additional factor or secondary check before execution. |

## Remaining work

These are only the confirmed gaps from the initial V6/V7 source review. The
other V6 and V7 requirements remain `Not assessed`, as do all other ASVS
chapters. Deployed rate limits, notification delivery, operational controls,
performance, and customer/production evidence need separate
requirement-specific verification.

Recommended remaining remediation order is:

1. Implement current-password re-authentication, secure password changes, and
   an approved offline password blocklist/breach-check data source.
2. Implement account enumeration resistance, email verification, password
   recovery, and security notifications through the selected SMTP adapter.
3. Implement and verify the agreed hardware-key-only WebAuthn factor, recovery
   controls, and deployment trust policy.
4. Continue requirement-by-requirement V6/V7 review, then assess all remaining
   ASVS chapters and update matrix rows only when exact evidence is verified.
