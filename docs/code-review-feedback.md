# Javara: Code Structure Review

Date: 2026-11-22
Branch: postgres (commit c9e64db)
Scope: all 53 production classes in src/main/java (about 3,000 lines), the Flyway migrations, pom.xml and the setup scripts.

Note: Java was not installed on the machine used for this review, so the code was not compiled and the tests were not run. All findings come from reading the source.

File references are relative to the repository root and use the form `path:line`.

## Summary

This is a well-built Spring Boot 4 / Java 25 API, and its security design is better than in most projects of this size. The main problems are a few gaps in session handling, error handling that is split across two systems, and some repeated code.

---

## What's done well

- **Modern Java used properly.** Requests, responses and config are records, and the config records check their own values when they are built (JwtProperties, CorsProperties). A bad config stops the app at startup instead of failing later.
- **Refresh tokens are handled well.** Tokens are rotated on each use, stored only as SHA-256 hashes, and locked while being checked. Reusing an old token revokes all of that user's sessions (`src/main/java/com/elsyah/tech/javara/service/AuthService.java:57-88`).
- **Unique emails survive soft delete.** A partial unique index (`WHERE deleted_at IS NULL`) enforces one email per active user, and the error handler turns a duplicate-email race into a 409 response.
- **The rate limiter is solid.** It runs as a single Redis Lua script, so counting is atomic, and logins are refused if Redis is down rather than going unlimited.
- **Safe database defaults.** `ddl-auto: none`, `open-in-view: false`, and Flyway migrations. RequestIdFilter also cleans up its logging context correctly.
- **Good test volume.** There are about 3,000 lines of tests in 28 classes, split into unit and integration tests.

---

## High priority: security and correctness

1. **Changing a password doesn't log out other sessions.**
   `src/main/java/com/elsyah/tech/javara/service/UserService.java:198-220`
   `updateUser` re-hashes the password but leaves every refresh token valid. Someone who stole a token keeps access for up to 30 days.
   Fix: revoke the user's active refresh tokens when the password changes.

2. **The login response time reveals which emails exist.**
   `src/main/java/com/elsyah/tech/javara/service/UserService.java:261-264`
   An unknown email returns immediately, while a known one runs an Argon2 check that takes noticeably longer. Measuring the response time tells an attacker whether an email is registered.
   Fix: run a dummy hash check for unknown emails so both paths take the same time.

3. **The rate limiter breaks behind a proxy, and only covers login.**
   `src/main/java/com/elsyah/tech/javara/middleware/interceptor/LoginRateLimitInterceptor.java:24`
   It keys on `request.getRemoteAddr()`. Behind a load balancer, every user shares the proxy's IP, so all of them share one rate limit.
   Fix: set `server.forward-headers-strategy`. Also add rate limits to `/register`, which runs an expensive Argon2 hash and currently has no limit at all, and to `/refresh`.

4. **Seed data lives in a production migration.**
   `src/main/resources/db/migration/V3__seed_user.sql`
   This migration inserts a user with the plaintext password `TestingOnly` in every environment. Nobody can log in with it, because the password check rejects that format, but it permanently reserves that email.
   Fix: move seed data to a dev-only Flyway location that is enabled per profile.

5. **MD5 or SHA-256 can be chosen for new password hashes.**
   `src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java:33-38`
   If MD5 or SHA-256 is configured as the default, the service only logs a warning.
   Fix: keep them for checking old hashes, but refuse to start if one is set as the default.

6. **Logout needs a valid access token.**
   `src/main/java/com/elsyah/tech/javara/controller/AuthController.java:97-117`
   With cookie transport, once the 15-minute access token expires, `/logout` returns 401 and the refresh token stays valid.
   Fix: let logout work from the refresh token alone.

---

## Medium priority: design and structure

7. **HTTP status codes leak into the service layer.**
   `ServiceResult` carries an `HttpStatus`, so every controller method repeats the same "if error, return failure" block, about 10 times in total. Meanwhile, `ApiException` is never thrown anywhere.
   Fix: have services throw domain exceptions, such as `UserNotFoundException` or `EmailAlreadyExistsException`, and let `@RestControllerAdvice` map them to responses. Controllers then shrink to 3-4 lines each.

8. **Errors come back in three different shapes.**
   - The `ApiException` handler returns `ProblemDetail` (`src/main/java/com/elsyah/tech/javara/exception/ApiExceptionHandler.java:43-46`).
   - The other handlers return `ApiResponse`.
   - Validation failures (`MethodArgumentNotValidException`) aren't handled at all, so they fall back to Spring's default error body.
   Fix: choose one format for all errors.

9. **The user endpoints don't follow REST conventions.**
   `src/main/java/com/elsyah/tech/javara/controller/UserController.java:127-180`
   - Paths like `/findAllWithSelectedColumn` and `/findAllWithLimitAndOffset` contain verbs. Merge them into a single `GET /users?page=&size=` that returns a page with a total count.
   - An empty list currently returns 404. It should return 200 with `[]`.
   - `GET /users` returns every user with no limit.

10. **Updating a user always requires the password.**
    `PUT` forces clients to resend the password and runs Argon2 on every profile edit.
    Fix: use separate DTOs and endpoints for "update profile" and "change password".

11. **The database schema looks ported from MySQL.**
    Since this is the `postgres` branch:
    - IDs are `VARCHAR(36)` strings. Postgres has a native `uuid` type, which maps to `java.util.UUID`.
    - Timestamps use `TIMESTAMP` (no time zone) with `LocalDateTime.now()`, so token expiry depends on the server's time zone. Use `Instant` with `timestamptz`, and inject a `Clock` so time-dependent code is testable.

12. **Expired refresh tokens are never deleted,** so the `refresh_tokens` table grows forever.
    Fix: add a scheduled cleanup job.

13. **Integration tests run against your local development database** (README line 710).
    Fix: use Testcontainers for PostgreSQL and Redis so the tests are repeatable and can't damage local data.

---

## Low priority: code quality

- **Repeated code:**
  - Three separate SHA-256-to-hex helpers:
    - `src/main/java/com/elsyah/tech/javara/service/AuthService.java:139`
    - `src/main/java/com/elsyah/tech/javara/middleware/ratelimit/LoginRateLimiter.java:46`
    - `src/main/java/com/elsyah/tech/javara/utils/security/PasswordHashService.java:165`, which uses a slow `String.format` loop instead of `HexFormat`.
  - Cookie-reading logic written twice: in `AuthController.cookieValue` and in `CookieBearerTokenResolver.resolve`.
  - The list of public endpoints written twice: `src/main/java/com/elsyah/tech/javara/middleware/security/ApiSecurityConfiguration.java:30-34` and `:66-71`.
  - The role names "USER"/"ADMIN" hard-coded in `src/main/java/com/elsyah/tech/javara/middleware/security/JwtTokenConfiguration.java:40` instead of read from the `UserRole` enum.
  - The header name `X-XSRF-TOKEN` repeated as a string literal in several classes.

- **A constructor that exists only for tests.**
  `src/main/java/com/elsyah/tech/javara/service/UserService.java:51-53`
  `UserService(UserRepository)` creates its own `new PasswordHashService()` and ignores the configured algorithm. Remove it and pass a mock in the tests.

- **Package naming:**
  - `middleware` is an Express/Node term. Spring projects usually say `config`, `security` or `web`.
  - `utils.security.PasswordHashService` is a `@Service`, not a utility.
  - As the project grows, consider organizing by feature (`user/`, `auth/`, `common/`) instead of by layer.
  - `BootstrapAdminProperties` is enabled from `JwtTokenConfiguration`, which is an odd place for it.
  - `WelcomeRepository` is a repository class that only returns a constant string.

- **Small cleanups:**
  - Duplicate `PreAuthorize` import at `src/main/java/com/elsyah/tech/javara/controller/UserController.java:17` and `:20`.
  - A stray `;;` at `src/main/java/com/elsyah/tech/javara/controller/WelcomeController.java:7`.
  - Redundant `@ResponseStatus(HttpStatus.OK)` on methods that already return `ResponseEntity` (UserController lines 129 and 163).
  - Redundant `@Transactional(readOnly = false)`, since that is the default.
  - Missing `@Override` on `LoginRateLimitInterceptor.preHandle`.
  - An unused `CorsConfigurationSource` import and an unnecessary fully qualified `CorsConfiguration` in `CorsModuleConfiguration`.
  - `saveAndFlush` called on entities that are already managed inside a transaction; the flush isn't needed.
  - The `UserSelectColumn` projection selects `deletedAt`, which is always null because of `@SQLRestriction`.

- **Javadoc problems:**
  - `@contact` isn't a real Javadoc tag, so the `javadoc` tool will report errors.
  - The `=====` banner comments, and the "Getters and setters..." comment repeated six times in `UserEntity`, add noise.
  - The Javadoc for `UserService.login` stops mid-sentence ("This method performs").

- **Personal details in every response.**
  `src/main/java/com/elsyah/tech/javara/response/ApiResponse.java:26-28`
  `ApiResponse` adds the developer's name and LinkedIn link to every API response. That belongs in API docs, not in the response body.

---

## Suggested order

1. High-priority items 1-5: the security fixes, each a small change.
2. Medium-priority items 7-8: fixing the error-handling model removes the most code.
3. Items 9-11, when you next change the API or schema.
