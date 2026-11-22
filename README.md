# JAVARA — PostgreSQL REST API

This README describes the JAVARA PostgreSQL implementation on the `postgres` Git branch.

The repository also plans to maintain separate implementations for MySQL, SQL Server, Oracle, SQLite, MongoDB, and ScyllaDB. The code and documentation described here apply only to PostgreSQL.

## Table of Contents

- [Overview](#overview)
- [Documentation](#documentation)
- [Technology Stack](#technology-stack)
- [Current Features](#current-features)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Local Installation](#local-installation)
- [Quick Start](#quick-start)
- [Database Configuration](#database-configuration)
- [Flyway Migrations](#flyway-migrations)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Authentication and Authorization](#authentication-and-authorization)
- [Request and Response Format](#request-and-response-format)
- [Validation and Error Handling](#validation-and-error-handling)
- [Password Hashing](#password-hashing)
- [Redis Authentication Rate Limiting](#redis-authentication-rate-limiting)
- [Concurrency and Data Safety](#concurrency-and-data-safety)
- [Testing](#testing)
- [Docker and PostgreSQL](#docker-and-postgresql)
- [Production Readiness](#production-readiness)
- [Known Limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)

## Overview

This starter provides a Spring Boot REST API for basic user CRUD operations backed by PostgreSQL.

The application is organized into controller, service, repository, entity, request, projection, and response layers.

## Documentation

The [documentation index](docs/README.md) links to a structured guide and
step-by-step tutorials for setup, configuration, API use, and business
adoption.

Supported operations include:

- Create a user
- Retrieve all users
- Retrieve users with selected columns
- Retrieve users with pagination
- Retrieve one user by ID
- Update a user
- Soft-delete a user
- Detect duplicate active emails
- Prevent concurrent lost updates

## Technology Stack

- Java 25
- Spring Boot 4.2
- Spring Web MVC
- Spring Data JPA
- Hibernate
- PostgreSQL 16
- Flyway
- Bean Validation
- Spring Security Crypto
- Bouncy Castle
- JUnit 5
- Mockito
- AssertJ
- Maven Wrapper

## Current Features

### Implemented

- PostgreSQL JPA repository
- UUID-style string user IDs
- Create and update validation
- Soft delete through `deleted_at`
- Active-user email uniqueness
- Email reuse after soft delete
- Entity `@Version` field
- Pessimistic row locking for update and delete
- HTTP 409 conflict responses
- Multiple password hash formats
- Argon2 password encoding for new and updated passwords
- JWT access tokens with rotating, revocable opaque refresh tokens
- Configurable Bearer or HttpOnly cookie token transport
- CSRF protection for cookie transport
- USER/ADMIN roles with user ownership checks
- Public user registration and configurable initial ADMIN bootstrap
- Redis-backed authentication rate limiting
- Standard success and error response wrapper
- Selected-column projection queries
- Pagination through Spring `Pageable`
- Unit tests
- Repository tests
- Concurrent integration tests against PostgreSQL

### Not Implemented Yet
- API versioning beyond the current URL prefix
- Audit logging
- Production secrets management
- Full pagination metadata
- PATCH endpoint

These items are intentionally not represented as implemented in this guide.

## Project Structure

```text
config/
└── application.yml   # Local runtime configuration created by make setup/install
src/
├── main/
│   ├── java/com/elsyah/tech/javara/
│   │   ├── controller/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── projection/
│   │   ├── repository/
│   │   ├── request/
│   │   ├── response/
│   │   ├── service/
│   │   └── utils/
│   │       └── security/
│   └── resources/
│       └── db/migration/
└── test/
    └── java/com/elsyah/tech/javara/
        ├── test/unit/
        └── test/integration/
```

Unit tests are organized below `test.unit`. Concurrent integration tests are organized below `test.integration`.

## Requirements

The project uses the Maven Wrapper. `make install` can install a missing JDK 25 and Docker/Compose after confirmation. On macOS, Homebrew must already be installed. Linux installation is best-effort on Debian/Ubuntu, Fedora, RHEL-compatible distributions, Arch, and openSUSE; unrecognized distributions receive manual setup instructions.

## Local Installation

### Install local dependencies

```bash
make install
```

This checks for JDK 25 and Docker Compose, requests confirmation before package installation, creates local-only credentials, and starts PostgreSQL and Redis containers. It does **not** start the REST API, so developers can continue editing the code before running it.

```bash
make run
```

The containers bind to localhost only. PostgreSQL data is kept in a named Docker volume. Use `make db-down` to stop the containers without deleting the data. `make db-reset` permanently deletes local database data and requires typing `DELETE DATA` for confirmation.

### Use existing PostgreSQL and Redis services

If PostgreSQL and Redis are already available, configure them instead of installing Docker-managed services:

```bash
make setup
```

The setup first offers to install JDK 25 if needed, then asks for service connection details, initial ADMIN email/password, and the token transport. It generates an HS256 signing key, checks TCP reachability when `nc` is available, then saves the runtime configuration in `config/application.yml` with restricted file permissions. This local file is excluded from Git. `make run` loads this file as the application's configuration.

The setup check confirms that the host and port are reachable; the application verifies database and Redis credentials when it starts.

On the first successful startup, the application creates the configured ADMIN account only if that email is unused. It refuses to promote an existing USER. After the ADMIN is created, remove the bootstrap password from `config/application.yml` and set `app.security.bootstrap-admin.enabled` to `false`.

If `config/application.yml` was created before JWT support and has no `app.security.jwt.signing-key`, rerun `make setup` or `make install`, accept replacing the old configuration, and enter the service and initial ADMIN settings again before starting the API.

### Configure browser CORS

Edit `config/application.yml` to allow a specific frontend origin:

```yaml
app:
  web:
    cors:
      enabled: true
      allowed-origins:
        - https://erp.example.com
      allow-credentials: true
```

Origins must include the scheme and optional port. Cookie transport with CORS enabled requires credentials and explicit origins; wildcard origins are not allowed with credentials. Cookie transport also requires a CSRF token from `GET /api/v1/auth/csrf` to be sent in the `X-XSRF-TOKEN` header on state-changing requests.

Compression, request IDs, security headers, and authentication rate limits are controlled independently by `server.compression.enabled`, `app.web.request-id.enabled`, `app.security.headers.enabled`, and `app.middleware.rate-limit.<endpoint>.enabled`. The HTTP listener remains active; configure its `server.address` and `server.port` instead.

## Quick Start

For a local Docker-managed database and Redis:

```bash
make install
make run
```

For existing database and Redis services:

```bash
make setup
make run
```

The API starts at:

```text
http://localhost:8080
```

## Database Configuration

The local JDBC connection in the Quick Start uses:

| Setting | Default |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | Generated local default: `javara` |
| Username | Generated local default: `javara` |
| Password | Generated locally and stored in the ignored `.env` and `config/` files |

`open-in-view` is disabled. Database work is performed inside service transactions instead of during response serialization.

## Flyway Migrations

Flyway automatically applies migrations from:

```text
src/main/resources/db/migration
```

### Migration List

| Migration | Purpose |
|---|---|
| `V1__create_user.sql` | Creates the `users` table and indexes, including active-user email uniqueness and optimistic locking |
| `V2__create_refresh_tokens.sql` | Creates hashed refresh-token storage |
| `V3__seed_user.sql` | Historical migration that inserts an example user |
| `V4__remove_demo_user.sql` | Removes the historical demo account and its refresh tokens |

Fresh databases run all migrations in order and do not retain a demo account.
Do not modify migration files that have already been applied. Add a new
migration whenever the database schema changes.

Check migration history:

```bash
docker exec postgres-db psql -U javara_app -d javara_db \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## Running the Application

Run tests:

```bash
make test
```

Build the application:

```bash
make build
```

Run the application:

```bash
make run
```

Open a PostgreSQL prompt:

```bash
docker exec -it postgres-db psql -U javara_app -d javara_db
```

## API Reference

Base URL:

```text
/api/v1
```

Except for `/`, registration, login, refresh, and the cookie-mode CSRF endpoint, requests require authentication. The user-management list/create routes are ADMIN-only; ID-based routes require the matching USER or an ADMIN.

### Welcome

```http
GET /
```

Example:

```bash
curl http://localhost:8080/
```

Response:

```json
{
  "welcome": "Welcome"
}
```

### Create User

Public account creation uses `POST /api/v1/auth/register`; it always creates a `USER`. The user-management create endpoint below is restricted to ADMIN accounts.

```http
POST /api/v1/users
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Example:

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <admin-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

This endpoint is ADMIN-only. Public registration is documented below.

Duplicate active email status: `409 Conflict`

### Authentication and Authorization

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Example:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

Login:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

In Bearer mode (the default), a successful login returns an access JWT and refresh token in the response. Send the access JWT as `Authorization: Bearer <accessToken>` for protected endpoints. Refresh with `POST /api/v1/auth/refresh` and `{"refreshToken":"..."}`; each successful refresh rotates the opaque refresh token. Logout at `POST /api/v1/auth/logout` requires only the current refresh token in the same JSON shape; the access token is not required.

Access tokens expire after 15 minutes by default. Refresh tokens expire after 30 days, are stored in PostgreSQL only as SHA-256 hashes, and are revoked on rotation or logout. Reuse of a revoked refresh token revokes the user's other active refresh tokens. Changing a user's password revokes all of that user's active refresh tokens; already-issued access JWTs remain valid until their short expiry.

Set `app.security.jwt.transport: COOKIE` to store tokens in HttpOnly cookies instead. The access cookie is sent to API routes; the refresh cookie is scoped to `/api/v1/auth`. Cookie mode enables CSRF protection: first call `GET /api/v1/auth/csrf`, then send its token in `X-XSRF-TOKEN` for POST/PUT/PATCH/DELETE requests. Logout works with the refresh cookie even after the access cookie expires. Cookies are Secure by default; cross-site deployments must configure `SameSite: None`, HTTPS, CORS credentials, and explicit allowed origins.

`/` and `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/refresh` are public. All `/api/v1/users/**` routes require authentication. USER accounts can access their own ID-based user routes; list and create operations, and all other-user management, require ADMIN.

The initial ADMIN is created from `app.security.bootstrap-admin.email` and `.password` on startup when `enabled: true`. A matching existing USER is never promoted automatically; startup fails with a clear error instead.

Invalid credentials and inactive accounts return the same `401 Unauthorized` message. If a stored password uses an older supported format, it is upgraded to the configured algorithm after successful login.

Too many login requests from one client IP return `429 Too Many Requests` with a `Retry-After` header. If Redis is unavailable, login requests fail closed with `503 Service Unavailable`.

### Find All Users

```http
GET /api/v1/users
```

```bash
curl http://localhost:8080/api/v1/users
```

Success status: `200 OK`

Empty result: `404 Not Found`

### Find All Users with Selected Columns

```http
GET /api/v1/users/findAllWithSelectedColumn
```

```bash
curl http://localhost:8080/api/v1/users/findAllWithSelectedColumn
```

### Find All Users with Pagination

```http
GET /api/v1/users/findAllWithLimitAndOffset?page=0&size=10
```

```bash
curl "http://localhost:8080/api/v1/users/findAllWithLimitAndOffset?page=0&size=10"
```

The Spring `Pageable` parameters are:

- `page`: zero-based page number
- `size`: number of records per page
- `sort`: optional sort expression; results are stabilized by a unique ID tie-breaker

The default page size is 20 and the maximum is 100. Requests outside the range 1–100 return `400 Bad Request`.

### Find User by ID

```http
GET /api/v1/users/{id}
```

Set `USER_ID` to an ID returned by user creation or registration. No demo user is seeded.

```bash
USER_ID="<id-from-create-or-register-response>"
curl "http://localhost:8080/api/v1/users/$USER_ID"
```

Success status: `200 OK`

Missing user status: `404 Not Found`

### Find User by ID with Selected Columns

```http
GET /api/v1/users/findUserByIdWithSelectedColumns/{id}
```

```bash
curl "http://localhost:8080/api/v1/users/findUserByIdWithSelectedColumns/$USER_ID"
```

### Update User

```http
PUT /api/v1/users/{id}
Content-Type: application/json
```

```bash
curl -X PUT "http://localhost:8080/api/v1/users/$USER_ID" \
  -H "Content-Type: application/json" \
  -d '{"email":"updated@example.com","password":"newpassword123","isActive":true}'
```

Success status: `200 OK`

Missing user: `404 Not Found`

Duplicate email: `409 Conflict`

Concurrency conflict: `409 Conflict`

This endpoint is currently PUT-style and replaces the mutable user fields in its request.

### Delete User

```http
DELETE /api/v1/users/{id}
```

```bash
curl -X DELETE "http://localhost:8080/api/v1/users/$USER_ID"
```

Deletion is soft:

- `deleted_at` is set to the current timestamp
- The row remains in PostgreSQL
- Normal JPA queries exclude it because of `@SQLRestriction`
- The email can be reused by a new active user

Success status: `200 OK`

Missing or already soft-deleted user: `404 Not Found`

## Request and Response Format

### User Request

| Field | Type | Validation |
|---|---|---|
| `email` | string | Required, valid email, maximum 255 characters |
| `password` | string | Required, 8 to 72 characters |
| `isActive` | boolean | Optional on update |

Currently, create always sets `isActive` to `true`; `request.isActive()` is not used during creation.

### Standard Success Response

```json
{
  "developer": "Elmansyah",
  "httpCode": 200,
  "message": "User found",
  "success": true,
  "version": "v1",
  "contact": "https://www.linkedin.com/in/elmansyah",
  "data": {}
}
```

Additional fields may be included. Reserved fields cannot be overwritten.

### Standard Failure Response

```json
{
  "developer": "Elmansyah",
  "httpCode": 404,
  "success": false,
  "version": "v1",
  "contact": "https://www.linkedin.com/in/elmansyah",
  "error": "User not found"
}
```

## Validation and Error Handling

Validation is performed with Bean Validation:

- Invalid email returns `400 Bad Request`
- Blank email or password returns `400 Bad Request`
- Password shorter than 8 characters returns `400 Bad Request`
- Password longer than 72 characters returns `400 Bad Request`

The exception handler handles:

- `ApiException`
- Data integrity violations
- Active-user duplicate email constraint
- Optimistic locking conflicts
- Pessimistic locking conflicts

The active email index is:

```sql
idx_users_email_active_unique
```

A duplicate active email returns:

```json
{
  "httpCode": 409,
  "success": false,
  "error": "Email already exists"
}
```

## Password Hashing

New and updated passwords use the algorithm configured for the application. The default is Argon2.

Password hashing utilities are located in:

```text
com.elsyah.tech.javara.utils.security
├── PasswordAlgorithm
└── PasswordHashService
```

Choose the algorithm in `config/application.yml`:

```yaml
app:
  security:
    password:
      algorithm: ARGON2
```

Supported configuration values are `ARGON2` and `BCRYPT`. The setting is case-insensitive. For example, set it to `BCRYPT` to make create/update operations encode new passwords with BCrypt.

Argon2 is the recommended default. SHA-256 and MD5 are accepted only for verifying legacy password hashes; they cannot be selected for new password hashes.

Supported formats are:

| Format | Prefix | Usage |
|---|---|---|
| Argon2 | `{argon2}` | Default for new passwords |
| BCrypt | `{bcrypt}` or raw `$2...` | Legacy verification and migration |
| SHA-256 | `{sha256}` or `sha256:` | Legacy verification and migration |
| MD5 | `{md5}` or `md5:` | Legacy verification only |
| Raw SHA-256 | 64 hexadecimal characters | Legacy verification |
| Raw MD5 | 32 hexadecimal characters | Legacy verification |

Example Argon2 value:

```text
{argon2}$argon2id$v=19$m=65536,t=3,p=4$...
```

`PasswordHashService.matches(...)` verifies a raw password against any supported format. `requiresUpgrade(...)` identifies hashes that should be upgraded to the currently configured algorithm after a successful authentication flow. A successful login transparently upgrades a legacy hash.

Legacy formats remain verifiable for compatibility, but:

- Argon2 is the default for new passwords.
- Only Argon2 and BCrypt can be selected for new passwords.
- SHA-256 and MD5 hashes are upgraded to the configured algorithm after the user's next successful login.

## Redis Authentication Rate Limiting

The login, registration, and refresh endpoints use independent Redis fixed-window counters per client IP. Redis stores only short-lived counters; PostgreSQL remains the source of truth for application data. The limiter runs before request validation and password hashing, so malformed requests also count toward the limit.

Defaults allow 10 login requests per IP per minute, 5 registrations per IP per hour, and 30 refresh requests per IP per minute. Configure each policy independently:

```yaml
app:
  middleware:
    rate-limit:
      login:
        max-requests: 10
        window: 1m
        key-prefix: javara:local:login
      register:
        enabled: true
        max-requests: 5
        window: 1h
        key-prefix: javara:local:register
      refresh:
        enabled: true
        max-requests: 30
        window: 1m
        key-prefix: javara:local:refresh
```

Each endpoint accepts `enabled`, `max-requests`, `window`, and `key-prefix`. Spring environment variable equivalents use the `APP_MIDDLEWARE_RATE_LIMIT_<LOGIN|REGISTER|REFRESH>_` prefix, for example `APP_MIDDLEWARE_RATE_LIMIT_REGISTER_MAX_REQUESTS`. Use distinct key prefixes for environments sharing a Redis server. Redis connection settings use Spring Boot's `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_USERNAME`, and `SPRING_DATA_REDIS_PASSWORD` properties.

The counter increment and expiration are performed atomically in Redis. `server.forward-headers-strategy: framework` makes the servlet client address proxy-aware. Only enable this behind a trusted reverse proxy/load balancer that strips client-supplied forwarding headers and writes its own; otherwise clients can spoof their IP and bypass per-IP limits. The setup scripts enable this strategy and bind the local app to loopback. Disable it when directly exposing the application without a trusted proxy. Exceeded limits return `429` with `Retry-After`; Redis failure rejects the affected request with `503` rather than bypassing the limiter.

## Concurrency and Data Safety

The PostgreSQL implementation contains these concurrency protections:

### Unique Active Email

A partial unique index prevents two non-deleted users from using the same email:

```sql
CREATE UNIQUE INDEX idx_users_email_active_unique
ON users (email)
WHERE deleted_at IS NULL;
```

### Pessimistic Row Lock

Update and delete acquire a PostgreSQL row lock before mutating the entity.

This prevents simultaneous update/delete operations from bypassing each other and reducing lost-update risks.

### Optimistic Locking

The `version` column is managed by JPA `@Version`.

This provides an additional stale-state check. A stale entity update can return HTTP `409 Conflict`.

### Concurrent Create

The service first checks whether an email exists, then attempts `saveAndFlush`.

The unique index remains the final guarantee if multiple requests check and insert simultaneously.

### Concurrent Update

Pessimistic locking serializes update and delete operations for the same row.

Concurrent requests against different rows can still proceed independently.

## Testing

The test suite has separate unit and integration package areas.

### Unit Tests

Unit tests are under:

```text
src/test/java/com/elsyah/tech/javara/unit
```

They cover:

- Controllers
- Services
- Repositories
- Request validation
- Response wrappers
- Exception handling
- Entity and projection mappings

### Concurrent Integration Tests

Concurrent integration tests are under:

```text
src/test/java/com/elsyah/tech/javara/integration
```

They cover:

- Duplicate email creation through concurrent threads
- Concurrent updates
- Concurrent deletes
- Concurrent update/delete operations

Run everything:

```bash
make test
```

Integration tests use isolated PostgreSQL 16 and Redis 7 Testcontainers by default and require a running Docker daemon. `make install` can install Docker when it is unavailable. Test containers are temporary and do not use or modify the application's local database.

To use dedicated test services instead, set all of these environment variables before running tests:

```text
JAVARA_TEST_POSTGRES_URL
JAVARA_TEST_POSTGRES_USERNAME
JAVARA_TEST_POSTGRES_PASSWORD
JAVARA_TEST_REDIS_HOST
JAVARA_TEST_REDIS_PORT
```

For Redis authentication, also set `JAVARA_TEST_REDIS_USERNAME` and `JAVARA_TEST_REDIS_PASSWORD`. The PostgreSQL database and Redis instance must be dedicated to tests because integration tests create and delete test data. Partial `JAVARA_TEST_*` configuration is rejected; tests never fall back to `config/application.yml`.

Run only concurrent integration tests:

```bash
./mvnw -Dtest='UserConcurrentIntegrationTests' test
```

The same Testcontainers or dedicated-service configuration applies to individual integration tests.

## Docker and PostgreSQL

Start PostgreSQL:

```bash
docker start postgres-db
```

Stop PostgreSQL:

```bash
docker stop postgres-db
```

Connect to the database:

```bash
docker exec -it postgres-db psql -U javara_app -d javara_db
```

Useful SQL commands:

```sql
\dt
\d users
SELECT * FROM flyway_schema_history;
SELECT id, email, is_active, deleted_at, version
FROM users;
```

## Production Readiness

The current implementation is suitable as a strong starter, learning project, or foundation for a small service. It is not yet complete for an internet-facing production service.

Additional production work is required before handling real accounts or sensitive data.

### Required Before Production

1. Configure signing/database credentials through a production secret manager.
2. Configure Redis authentication, TLS, availability, and monitoring for production rate limiting.
3. Add deadlock/lock timeout handling.
4. Add structured logging and request tracing.
5. Add health checks and metrics.
6. Add HTTPS/TLS at the gateway or reverse proxy.
7. Add backup and restore procedures.
8. Add database connection-pool monitoring.
9. Add CI/CD database migration checks.
10. Add load and concurrent integration tests to CI.

Review the [production and business adoption guide](docs/guide/production-and-adoption.md)
for product boundaries such as tenant isolation, account recovery, and
registration policy.

## Known Limitations

- Access JWTs are not revoked immediately by logout; they remain valid until their configured expiry.
- Authentication rate limiting uses fixed windows keyed by the proxy-resolved client IP.
- The application returns `503` for limited authentication requests if Redis is unavailable.
- Create ignores `request.isActive()` and always creates an active user.
- Concurrent HTTP behavior should also be tested through MockMvc or a running HTTP server.
- Lock timeout and deadlock retry behavior are not yet standardized.
- A generic database outage currently is not distinguished from a constraint conflict.
- Login/refresh tokens are returned in the response only when Bearer transport is configured.
- The service does not provide tenant isolation, email verification, or password recovery.

## Troubleshooting

### Connection Refused

Confirm PostgreSQL is running:

```bash
docker ps
```

Confirm port `5432` is available:

```bash
docker port postgres-db
```

### Authentication Failed

Confirm the configured username and password match PostgreSQL:

```bash
docker exec postgres-db psql -U javara_app -d javara_db -c "SELECT current_user;"
```

### Flyway Migration Error

Check migration history:

```bash
docker exec postgres-db psql -U javara_app -d javara_db \
  -c "SELECT * FROM flyway_schema_history;"
```

Do not repair or change an applied migration file. Add a new migration or explicitly repair the Flyway history only in a controlled environment.

### Duplicate Email

The API returns `409 Conflict` when an active user already has the email.

The same email can be reused after the existing user is soft-deleted.

### Concurrent Conflict

HTTP `409 Conflict` means that the resource changed while the request was being processed or another request won the database conflict.

The client should read the latest resource state and retry safely if the operation is safe to repeat.

### Integration Test Cannot Connect

Verify that PostgreSQL is running and accepts local connections:

```bash
psql "postgresql://javara_app@localhost:5432/javara_db"
```

Ensure the test and application use the same database version and migrations.
