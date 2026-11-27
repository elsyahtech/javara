# Authentication and Session Management Review

## Status and scope

This is an evolving repository evidence review for ASVS v5.0.0 chapters V6
(Authentication) and V7 (Session Management). It is not an independent audit,
penetration test, certification, or production-readiness claim.

- Source baseline: `aacdb7858552fe2cfd35eb40ae04808d0e33c120`, as pinned in
  [assessment scope](scope.md).
- Initial assessment documentation checkout: `0a1c7108a4b5ba74cbb2c99eb36d943464b75381`;
  changes from the source baseline at that point were assessment
  documentation only.
- Current review date: 2026-11-27. Remediation evidence below reflects commit
  `b009b8a` and local manual tests, not an independent reassessment.
- Initial baseline: 21 requirements marked `Fail`. Current disposition: 4
  `Fail`, 17 `Pending` retest/evidence, and 324 `Not assessed`.
- No requirement is marked `Pass` or excluded.

The historical findings below are confirmed for the pinned baseline. Current
dispositions account for remediation in the worktree but are not an
independent reassessment or approved exception. The
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

After subsequent authentication, session, password-policy, and email changes,
the complete Maven test suite also passed: 198 tests, 0 failures, 0 errors,
0 skipped (`bash scripts/maven.sh test`).

For the current AUTH-06 worktree, `make test` passed 225 tests with 0 failures,
0 errors, and 0 skipped. The focused AUTH-06/authentication/email selection
passed another 24 tests after two additional login-lock tests were added. These
are automated results; a partial local manual AUTH-06 retest is recorded below.

### AUTH-06 local retest checklist

Use a disposable verified account, not an account needed for ongoing work.
Temporarily lower the lock policy in the ignored `config/security.yml`,
restart the application, and restore the desired configuration afterward:

1. Run `make security-setup`, then set failed-login `threshold: 2`,
   `window: 5m`, `lock-duration: 1m`, and
   `action: LOCK_AND_NOTIFY`. Submit two incorrect passwords, confirm both
   responses remain generic, and check for the security-alert email. A correct
   password should remain rejected during the temporary lock; sign-in should
   work again after expiry.
2. Set the failed-MFA threshold to `2` and its action to `LOCK_AND_NOTIFY`.
   Keep `app.security.mfa.max-attempts` above `2` so the challenge remains
   available long enough to exercise the account-level lock. Submit two
   incorrect OTPs, confirm the alert and temporary lock, then verify the
   account can sign in after the lock expires.
3. Perform a password change and an MFA enrollment/preference change, then
   confirm the configured security-change notification behavior.
4. To check source-change detection, authenticate successfully from a second
   network address. Do not spoof forwarded headers; configure and trust a real
   reverse proxy before testing proxy-provided client addresses.

Record only outcomes and status codes, not passwords, OTPs, tokens, or raw
network addresses. Keep AUTH-06 `Pending` until these local checks and the
requirement-specific review are completed.

#### Partial local manual retest — 2026-11-27

The user tested two different verified accounts with the configured threshold
of five attempts:

- Five incorrect password logins each returned `401` with the same generic
  `"Invalid email or password"` response. On the fifth attempt, the configured
  temporary lock triggered and the suspicious-sign-in alert email arrived
  with the lock expiry.
- A login on a separate account with email OTP MFA sent the expected
  anti-phishing warning. Five incorrect OTP verifications each returned
  `401` with the generic invalid/expired/used challenge response. On the fifth
  attempt, the configured temporary lock triggered and the MFA security alert
  email arrived with the lock expiry.

The user then tried the correct password while the account was locked; the
login still returned `401` with the same generic
`"Invalid email or password"` response. After waiting 15 minutes, login
succeeded with `200` and access/refresh tokens, confirming sign-in recovery
after the temporary lock. The new-source and security-change notification
paths have not yet been verified, so AUTH-06 remains `Pending`.

Local manual API checks on 2026-11-26, using the local setup and Gmail SMTP,
observed:

- In the registration-verification-login flow, public registration returned
  `201` and the verification email was delivered. Login before verification
  returned `401` with the generic `"Invalid email or password"` response.
  After following the verification link, login returned `200` and issued
  access and refresh tokens. A separate verification-resend request returned
  `202`. Account identifiers and credentials are omitted.
- After verification, a login attempt with an incorrect password returned
  `401` and the same generic `"Invalid email or password"` response.
- A login attempt for an account already marked inactive returned `401` with
  the generic `"Invalid email or password"` response. This confirms inactive
  accounts cannot log in.
- The user clarified that a protected sessions request using a token issued
  before account deactivation was also attempted and rejected. The exact HTTP
  status was not restated in that follow-up; this is evidence of session
  invalidation after deactivation, distinct from the user-deletion check.
- In a single-session password-change check, session listing returned `200`;
  changing the password from that authenticated session returned `200`; and
  a subsequent session-list request using its pre-change access token
  returned `401`. This confirms locally that password change invalidates the
  current access-token session.
- In a separate cross-account check, changing the USER account's password
  returned `200`; that account's pre-change access token then received `401`,
  while the separate ADMIN account could still access its own sessions
  endpoint. This confirms the password change invalidates the changed user's
  session without disrupting an unrelated account.
- The user clarified that a separate password-change test used two active
  sessions belonging to the same USER account; both pre-change sessions were
  invalidated successfully. The precise response status for the second
  session was not restated.
- After that change, login with the old password returned `401` with the
  generic `"Invalid email or password"` response, while login with the new
  password returned `200` and issued fresh tokens. This confirms the old
  password was no longer accepted and the new password was active.
- Logging out the session created by that new-password login succeeded; a
  subsequent session-list request using its access token was rejected. This
  further confirms locally that logout invalidates the session. Exact HTTP
  status codes for this check were not provided.
- Separately, refresh returned `200`, and logout returned `200` when given
  the refresh token in the request body.
- In a separate single-session check, `GET /api/v1/auth/sessions` returned
  `200` and listed the active session. Logging out with that session's refresh
  token returned `200`; a subsequent request to the sessions endpoint using
  its previously issued access token returned `401`. This confirms local
  current-session logout invalidates access to protected endpoints. Tokens and
  session identifiers are omitted.
- In a local idle-timeout check, after leaving the admin session inactive for
  more than 15 minutes, `GET /api/v1/auth/sessions` returned `401` and refresh
  returned `401` with `"Refresh token is invalid or expired"`. Since the
  access token also expires after 15 minutes, its `401` alone does not isolate
  idle-timeout behavior; rejection of refresh after the idle period is the
  relevant observation.
- With two sessions active, `GET /api/v1/auth/sessions` returned `200` from
  both sessions and listed both, marking the caller as `current`. Logging out
  one session returned `200`; its access token then received `401`, while the
  other session remained active and was the only session listed. Logging out
  the remaining session also returned `200`, and its access token then
  received `401`. This verifies independent session listing and per-session
  logout locally.
- At the configured concurrent-session limit, an additional login returned
  `409` with `"Maximum number of active sessions reached"`. After logging out
  one session, another login succeeded, and the session list showed five
  active sessions. This verifies the local session cap and slot reuse.
- In a separate two-session check, a request authenticated with one session
  targeted the other session's ID at
  `POST /api/v1/auth/sessions/{sessionId}/terminate`. It returned `200` with
  one session terminated. The caller's token remained valid and listed only
  its own session; the targeted session's access token received `401`. This
  verifies targeted termination of another session locally. Tokens and
  session identifiers are omitted.
- `POST /api/v1/auth/sessions/terminate-all` returned `200` and reported four
  sessions terminated. A subsequent request to list sessions with a
  pre-termination access token returned `401`. This verifies local
  all-session termination, including invalidation of the caller's session.
- In a later repeat, the original access token had expired; refreshing the
  still-active session returned `200`. Calling
  `POST /api/v1/auth/sessions/terminate-all` with the newly issued access
  token and current password then returned `200` and reported one session
  terminated. A post-termination request using that access token has not yet
  been reported.
- Using an administrator account, listing users returned `200`; deleting a
  test user returned `200` and set its deletion timestamp. A protected
  sessions request using that user's pre-deletion access token then returned
  `401`. Account identifiers and email addresses are omitted.
- Using an administrator account, terminating a target user's sessions
  returned `200` with one session terminated. A subsequent protected sessions
  request using the target user's pre-termination access token returned `401`.
  This verifies the local admin-initiated session-termination flow.
- A targeted session-termination attempt later returned `429` (`Too many
  requests`). This is consistent with the configured per-client rate limit;
  a subsequent targeted termination succeeded as described above.
- In a self-service email-change check, the request returned `202` and the
  confirmation email was delivered. Its link was described as single-use and
  expiring after 24 hours. The existing session remained active before
  confirmation. Confirmation returned `200`; afterward, the pre-change access
  token received `401`, login with the old email returned the generic `401`,
  and login with the new email returned `200` with fresh tokens. This confirms
  the local email-change, confirmation, and session-revocation flow. Email
  addresses and the confirmation link are omitted.
- A new-session notification email was received during local login testing.
- The password-change security notification email was also received locally.
- On 2026-11-27, the user enrolled `EMAIL_OTP` from an authenticated session.
  The enrollment email was received with a five-minute code, and verification
  returned `200` with Email OTP enabled and preferred. As designed, enrollment
  revoked the active sessions: a subsequent sessions request with the old
  access token returned `401`. A new login selecting `EMAIL_OTP` returned
  `202` with a challenge and sent another code. OTP verification succeeded and
  returned access/refresh tokens; `GET /api/v1/auth/sessions` with the new
  access token returned `200` and listed the new session as current. OTP values,
  tokens, challenge IDs, session IDs, and recipient addresses are omitted.
  Replaying the same OTP verification after successful completion returned
  `401` with the generic `"MFA challenge is invalid, expired, or has already
  been used"` response, confirming that the challenge cannot be reused.
  A security-awareness sentence telling recipients never to share the code,
  including with anyone claiming to be support, was added afterward and is
  covered by a focused notification unit test. On a subsequent local check,
  the OTP email was received with this updated warning and the configured
  “Powered by Javara Starter” attribution. The OTP value is omitted.
- After that MFA login, logout succeeded. A subsequent
  `GET /api/v1/auth/sessions` using the same access token returned `401`,
  confirming locally that logout invalidated this MFA-created session.
- In a public-registration retest, a new address received the generic `202`
  response and verification email. Login before verification returned the
  generic `401`; following the verification link returned `200`, and reusing
  the consumed link returned `400`. After verification, login requesting
  `EMAIL_OTP` on an account without an enrolled factor returned `400`;
  ordinary login succeeded. Submitting registration again for the same
  verified address returned the same generic `202` response and did not send
  another verification email. This confirms the duplicate-registration
  response does not claim that a new account was created; response-equivalence
  is also covered by an integration test. Tokens, links, addresses, and
  credentials are omitted.
- In a local SMTP-failure simulation using an unavailable loopback endpoint,
  login still succeeded and the listener logged the `NEW_SESSION` event type
  and `MailSendException` class without logging the message contents. This
  verifies local failure handling and sanitized logging, not automatic retry;
  no durable outbox or retry mechanism is implemented.
- Initial SMTP authentication attempts failed. Delivery succeeded after the
  local SMTP credentials were corrected. This is not staging evidence; delivery
  is confirmed for local tests; retry behavior and production operational
  handling remain unverified.
- In an emergency password-reset and recovery check, the administrator's reset
  request returned `202`; the security notice was delivered to the user's
  verified address. A protected sessions request using the user's pre-reset
  session returned `401`, and login with the pre-reset password returned
  `401` with the generic `"Invalid email or password"` response.
- The administrator then authorized recovery. The request returned `202`, and
  the user received a single-use recovery link with a one-hour expiry. The
  previously invalidated password was rejected with an explanatory message;
  the recovery form remained available for another attempt. The user set a
  different password successfully. Login with the pre-reset password still
  returned `401`, while login with the newly set password succeeded. Passwords,
  tokens, account identifiers, and recipient addresses are omitted.

These are local smoke-test observations, not requirement-by-requirement ASVS
verification. Tokens, passwords, and recipient addresses are deliberately
excluded. No production or staging environment was assessed.

Evidence references for the committed remediation include:

- Authentication and password change: [`AuthController`](../../../src/main/java/com/elsyah/tech/javara/controller/auth/AuthController.java),
  [`AuthService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/AuthService.java),
  [`UserService`](../../../src/main/java/com/elsyah/tech/javara/service/user/UserService.java),
  [`AuthControllerTests`](../../../src/test/java/com/elsyah/tech/javara/unit/auth/controller/AuthControllerTests.java),
  and [`UserServiceTests`](../../../src/test/java/com/elsyah/tech/javara/unit/user/service/UserServiceTests.java).
- Session lifecycle: [`SessionManagementService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/SessionManagementService.java),
  [`SessionTokenValidator`](../../../src/main/java/com/elsyah/tech/javara/service/auth/SessionTokenValidator.java),
  [`SessionTokenValidatorTests`](../../../src/test/java/com/elsyah/tech/javara/unit/auth/service/SessionTokenValidatorTests.java),
  and [`UserConcurrentIntegrationTests`](../../../src/test/java/com/elsyah/tech/javara/integration/UserConcurrentIntegrationTests.java).
- Password datasets: [`PasswordPolicyService`](../../../src/main/java/com/elsyah/tech/javara/service/user/PasswordPolicyService.java),
  [`PasswordPolicyConfigurationTests`](../../../src/test/java/com/elsyah/tech/javara/unit/middleware/security/PasswordPolicyConfigurationTests.java),
  and [`BootstrapAdminServiceTests`](../../../src/test/java/com/elsyah/tech/javara/unit/user/service/BootstrapAdminServiceTests.java).
- Email verification and notices: [`EmailVerificationService`](../../../src/main/java/com/elsyah/tech/javara/service/email/EmailVerificationService.java),
  [`EmailMessageListener`](../../../src/main/java/com/elsyah/tech/javara/service/email/EmailMessageListener.java),
  [`EmailVerificationIntegrationTests`](../../../src/test/java/com/elsyah/tech/javara/integration/EmailVerificationIntegrationTests.java),
  and [`EmailNotificationServiceTests`](../../../src/test/java/com/elsyah/tech/javara/unit/email/service/EmailNotificationServiceTests.java).
- Emergency password reset and recovery: [`AdminPasswordResetController`](../../../src/main/java/com/elsyah/tech/javara/controller/user/AdminPasswordResetController.java),
  [`PasswordResetService`](../../../src/main/java/com/elsyah/tech/javara/service/auth/PasswordResetService.java),
  [`EmailVerificationIntegrationTests`](../../../src/test/java/com/elsyah/tech/javara/integration/EmailVerificationIntegrationTests.java),
  and migration [`V8__track_compromised_password_hashes.sql`](../../../src/main/resources/db/migration/V8__track_compromised_password_hashes.sql).

The review also inspected the password request/hash path, authentication and
user controllers, token lifecycle, JWT validation, and the production/API
guides. No production or staging environment was available or assessed.

## Remediation in commit `b009b8a`

The assessment baseline above is historical. The following remediation is
included in commit `b009b8a` and has not yet been independently re-assessed.
The original matrix findings remain baseline findings; no requirement is being
claimed as `Pass`.

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
- Self-service password change now uses
  `POST /api/v1/auth/password/change`, requires an authenticated USER/ADMIN and
  the current password, rejects reuse, rate-limits attempts, and revokes all
  sessions. The legacy user-update DTO no longer accepts a password. Cookie
  mode clears its authentication cookies after success.
- Password creation, self-service changes, and bootstrap-admin creation now
  check an operator-managed denylist and local compromised-password hashes.
  Startup fails with actionable messages if required files are missing,
  unreadable, undersized, empty, or malformed. Production/staging profiles
  cannot disable the policy, and illustrative sample mode is limited to
  local/development/test profiles. Setup seeds tiny example files so local
  startup works, but no production-quality dataset is bundled or independently
  verified; data provenance, licensing, freshness, coverage, and deployment
  remain operational evidence still required for assessment.
- Public registration now creates an unverified USER and issues a single-use,
  expiring email-verification token. Login rejects unverified accounts with a
  generic invalid-credentials response; existing and bootstrap accounts remain
  verified. Resend uses a generic `202` response.
- Self-service email changes require the current password and verification of
  the new address; the existing address remains active until confirmation.
  Completing an email change revokes the user's active sessions. The legacy
  user-update endpoint cannot change email or password; the admin email-change
  route requires the administrator's current password.
- SMTP delivery and security-notification events are implemented for
  verification, password changes, email changes, new sessions, user-requested
  password recovery, and emergency password-reset notices. Delivery
  occurs after commit and   SMTP exceptions are logged without message contents.
  There is no durable outbox or automatic retry, so successful delivery and
  operational handling remain incomplete evidence.

The focused regression command completed successfully with 48 tests,
0 failures, and 0 errors:

```text
bash scripts/maven.sh -Dtest=PasswordPolicyServiceTests,PasswordPolicyConfigurationTests,UserServiceTests,BootstrapAdminServiceTests,SecurityAuthorizationIntegrationTests test
```

This verifies only the tested repository behavior. Database-write cost on every
authenticated user request has not been load-tested. Token/timeout behavior
and session-termination edge cases still need requirement-specific retesting.
The configured password-data files are illustrative samples, not approved
production datasets; provenance, licensing, freshness, coverage, and
large-corpus performance remain open. Password-change email delivery has been
confirmed locally, but there is no durable outbox.
Context-specific password-word checks, production-verified phishing-resistant
MFA, sensitive-operation step-up, and suspicious-authentication detection
remain open. Public registration now uses a generic response to reduce
account-enumeration risk; local retesting remains pending. User-initiated
password recovery and administrator emergency randomization are implemented.
Emergency randomization invalidates the
credential, revokes sessions, locks sign-in, and sends an administrator-authored
notice; after out-of-band support verification, the user receives a one-time
link and chooses a new password. Automated integration coverage exists; local
SMTP-failure recovery and requirement-specific ASVS review remain pending.

## Initial baseline findings and current disposition

The evidence and gap descriptions in this table describe the pinned initial
baseline. `Pending` means remediation is present or partially evidenced in
commit `b009b8a`, but the complete ASVS requirement has not been verified.
Only `Pass` can indicate a verified requirement; none is claimed here.

### V6 - Authentication

| Finding | Requirement | Baseline evidence and gap | Current disposition |
|---|---|---|---|
| AUTH-01 | V6.1.2, V6.2.11 | No context-specific password-word list is documented or enforced. The password request validates presence, length, and email format; the password service only encodes and verifies hashes. See [`UserRequest`](../../../src/main/java/com/elsyah/tech/javara/request/user/UserRequest.java), [`PasswordHashService`](../../../src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java). | `Fail` — common/breached-password checks do not provide a customer/JAVARA context-word policy. |
| AUTH-02 | V6.2.3 | The baseline self-service `PUT /api/v1/users/{id}` accepted a password without current-password re-authentication. | `Pending` — dedicated authenticated password-change endpoint requires the current password and revokes sessions; regression tests and local change were successful. Retest against the complete requirement. |
| AUTH-03 | V6.2.4 | The baseline had no disallowed-password dataset check. | `Pending` — policy is applied at registration, password change, and bootstrap; production requires at least 3,000 entries. Only illustrative local data is present, so approved dataset evidence is outstanding. |
| AUTH-04 | V6.2.12 | The baseline had no breached-password check in password creation or update. | `Pending` — offline hash lookup is implemented and tested; the sample hash file is not a production breach corpus. Source, license, coverage, freshness, and production deployment evidence are outstanding. |
| AUTH-05 | V6.3.3 | The initial production guide listed multi-factor authentication as unavailable. Level 3 requires a phishing-resistant hardware-based factor unless an accepted rationale and comprehensive mitigations apply. See [production boundaries](../../guide/production-and-adoption.md). | `Fail` — login supports WebAuthn passkeys and email OTP, but MFA is optional by default, email OTP is not phishing-resistant, and no production authenticator or WebAuthn-only policy has been independently verified. |
| AUTH-06 | V6.3.5 | No mechanism was found to detect and notify users of suspicious successful or unsuccessful authentication attempts. | `Pending` — deployment-configurable detectors cover repeated failed password/MFA checks, a changed successful network source, and security-setting changes. Automated tests passed; local retest confirmed generic failure responses, alert/temporary-lock emails on the fifth password and MFA failures, rejection of the correct password during an active lock, and successful sign-in after waiting 15 minutes. Source-change and security-change paths, trusted-proxy review, and complete requirement-specific assessment remain. |
| AUTH-07 | V6.3.7 | The baseline had no notification flow for authentication-detail changes. | `Pending` — verification, email-change, new-session, and password-change messages were received locally; emergency-reset and recovery email paths have automated coverage. An SMTP failure was caught and logged without message contents. Durable retry/outbox and production operational evidence remain outstanding. |
| AUTH-08 | V6.3.8 | Login uses a generic invalid-credentials message, but duplicate public registration returned HTTP 409 and `"Email already exists"`. | `Pending` — public registration now returns the same generic `202` response for new and existing addresses, including database uniqueness races. Automated response-equivalence checks and local retest passed; complete requirement-specific review, including timing considerations, remains. |
| AUTH-09 | V6.4.3 | Users can request password recovery through a generic-response endpoint; reset tokens go only to the verified email address and users choose their own new password. | `Pending` — hashed single-use tokens, expiry, password policy enforcement, and session revocation are implemented. Local recovery succeeded with a new password; the previously invalidated password was rejected without consuming the recovery link. Requirement-specific recovery review remains. |
| AUTH-10 | V6.4.6 | Administrators can trigger emergency credential randomization but cannot see or choose the generated credential. The action revokes sessions and blocks login until support verifies the user and authorizes user-controlled recovery. | `Pending` — automated tests and local manual checks cover emergency randomization, notice delivery, rejection of the pre-reset session and password, administrator-authorized recovery, rejection of the invalidated password, and successful user-set recovery. SMTP-failure recovery and requirement-specific review remain. |

### V7 - Session Management

| Finding | Requirement | Baseline evidence and gap | Current disposition |
|---|---|---|---|
| SES-01 | V7.1.1 | The baseline did not document inactivity and absolute session lifetimes or the associated risk rationale. | `Pending` — local refresh was rejected after more than 15 minutes of inactivity, and an automated test covers expiry beyond the 12-hour absolute lifetime. Risk rationale and complete requirement-specific timeout/re-authentication assessment remain outstanding. |
| SES-02 | V7.1.2 | The baseline did not document or enforce a concurrent-session limit. | `Pending` — local testing confirmed the configured cap rejects an additional login with `409` and allows another login after a slot is freed. Requirement-specific edge cases and risk rationale remain outstanding. |
| SES-03 | V7.3.2 | The baseline refresh flow could extend sessions without preserving an absolute start time. | `Pending` — session start and absolute expiry are preserved across refresh; repeated-refresh and expiry-boundary evidence needs requirement-specific review. |
| SES-04 | V7.4.1 | The baseline revoked refresh tokens but left already-issued access JWTs usable until expiry. | `Pending` — local two-session logout, targeted termination, and all-session termination confirmed revoked access tokens receive `401`. Other revocation causes and the full requirement still need verification. |
| SES-05 | V7.4.2 | The baseline did not revoke all access/session state on user disablement or deletion. | `Pending` — local user deletion invalidated a pre-existing access token (`401`); the user also confirmed a pre-disablement token was rejected after account deactivation, though the status was not restated. Full requirement-specific verification remains outstanding. |
| SES-06 | V7.4.3 | The baseline password-change flow did not end already-issued access-token sessions. | `Pending` — local tests confirmed password change invalidates the changed user's session, including two sessions belonging to the same USER; an unrelated ADMIN session remained active. Full requirement-specific review remains outstanding. |
| SES-07 | V7.4.5 | The baseline had no administrator endpoint to terminate another user's sessions. | `Pending` — local admin-initiated termination returned `200` for one session, and the target's pre-termination access token then received `401`. Audit-trail and complete requirement-specific review remain outstanding. |
| SES-08 | V7.5.1 | The baseline allowed email updates without re-authentication or new-address verification. | `Pending` — self-service email change requires the current password and confirmation of the new address; local confirmation and login with the new address succeeded. |
| SES-09 | V7.5.2 | The baseline had no user session listing or individual/all-session termination endpoints. | `Pending` — listing, current-session logout, targeted termination of another session, and all-session termination were verified locally. The complete ASVS requirement still needs review. |
| SES-10 | V7.5.3 | Sensitive changes lacked a verified step-up or secondary factor. | `Fail` — MFA is enforced only in the login flow; sensitive operations do not yet require step-up verification. |

## Remaining work

These dispositions cover only the 21 requirements identified during the
initial V6/V7 review. The current matrix has 4 `Fail`, 17 `Pending`, and 324
`Not assessed`; no requirement is `Pass`. Other V6/V7 requirements and every
other ASVS chapter still require assessment. Deployed rate limits, notification
delivery, operational controls, performance, and customer/production evidence
need separate requirement-specific verification.

Recommended next steps are:

1. Retest the remaining session controls, including the absolute-expiry
   boundary across repeated refresh. Current-session
   logout, targeted termination, all-session termination, concurrent-session
   cap behavior, idle refresh rejection, deletion revocation, and token
   rejection after account disablement were verified locally or with focused
   automated tests.
2. Assess whether durable outbox/retry is required before relying on notices,
   and gather SMTP delivery/alerting evidence in staging. Local SMTP failure
   logging and delivery of verification, email-change, new-session, and
   password-change notifications were verified.
3. Select and supply approved password datasets with documented provenance,
   license, freshness, coverage, and production deployment evidence.
4. Address the confirmed gaps: context-specific password words, production
   verification and enforcement of phishing-resistant MFA, sensitive-operation
   step-up. Locally test suspicious-authentication detection and generic
   registration responses, user-initiated recovery, emergency randomization,
   support authorization, and recovery after SMTP failure.
5. Continue requirement-by-requirement V6/V7 review, then assess all remaining
   ASVS chapters and the multi-tenant and deployment/operations workstreams.
