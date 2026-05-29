# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Java 21**, **Spring Boot 3.5.9**, **Spring Cloud 2025.0.1**
- Multi-module Maven build (`pom` parent, no `spring-boot-starter-parent` — BOM imported via `dependencyManagement`)
- **PostgreSQL** + **Flyway** (auth-service only)
- Logback config is centralised per module under `src/main/resources/config/logback-spring.xml`; all modules log with the same pattern that includes the `requestId` MDC key

## Modules

| Module | Purpose | Port |
|---|---|---|
| `discovery-service` | Eureka server (runs as a 3-node cluster `eureka-1/2/3` in docker-compose) | 8761 |
| `api-gateway` | Spring Cloud Gateway (WebFlux). Auto-routes via Eureka with `lower-case-service-id` | 8090–8092 |
| `auth-service` | Authentication / authorisation, JWT + RBAC, PostgreSQL schema `iam` | 9001 |
| `Stub` | Test microservice used to validate gateway routing | — |

All Spring Boot modules require `SPRING_PROFILES_ACTIVE` to be set; there is no default. Valid profiles per module: `local`, `dev`, `qa`, `prod`. Only `local` ships sensible defaults — the other profiles expect every env var to be provided externally.

## Commands

Run from the repo root (parent pom). `./mvnw` is the Maven wrapper.

```bash
# Compile a single module (and its dependencies)
./mvnw -pl auth-service -am clean compile

# Package (JAR + build-info) a single module
./mvnw -pl auth-service -am clean package -DskipTests

# Run a module locally (needs SPRING_PROFILES_ACTIVE=local)
./mvnw -pl auth-service spring-boot:run -Dspring-boot.run.profiles=local

# Whole reactor
./mvnw clean install
```

When moving/renaming Java files, always `./mvnw -pl <module> clean compile` before restarting — stale `.class` files in `target/` shadow the new sources and produce `ConflictingBeanDefinitionException` or "endpoint not found" symptoms.

## Docker

`docker-compose.yml` builds `discovery-service` (3 replicas), `api-gateway` (2 replicas) and `auth-service` (1 replica). Each container reads `global.env` plus a per-instance `.env` file. PostgreSQL is **never** in compose — see "Database deployment per environment" below.

```bash
cp .env.example global.env   # fill SPRING_PROFILES_ACTIVE, EUREKA URLs, TZ
docker compose up --build
```

`.env` files and `logs/` are gitignored.

## Database deployment per environment

- **Local**: developer's own Postgres on host. `auth-service` container reaches it via `host.docker.internal:5432` (compose has `extra_hosts: "host.docker.internal:host-gateway"` for Linux). Host's Postgres must `listen_addresses = '*'` and allow the Docker bridge range in `pg_hba.conf`.
- **Dev / QA / Prod**: each environment runs on a **different server** with its own external Postgres instance (no Docker, no compose-managed DB). The microservice config does not change between environments — only `SPRING_DATASOURCE_URL`, credentials and other env vars do. In prod, append `?sslmode=require` to the URL and inject secrets via a secret manager (Vault, AWS Secrets Manager, Doppler).
- Containers themselves may run in compose / Kubernetes / ECS / systemd; that's orthogonal. The DB is always remote-managed.

## Naming convention

Each microservice has two distinct names:

- **`APP_NAME`** (`spring.application.name`, from `application.yaml`) — identifies the **service type**: `discovery-service`, `api-gateway`, `auth-service`. Used as the service identifier in Eureka (uppercased internally: `AUTH-SERVICE`).
- **`INSTANCE_HOSTNAME`** (env var) — identifies the **specific instance** using a short alias + index: `eureka-1/2/3`, `gateway-1/2`, `auth-1`. This is what Eureka registers per instance and what other services use as the network hostname.

Rules:
- In `docker-compose.yml`, the **service key**, **`container_name`** and **`hostname`** all mirror `INSTANCE_HOSTNAME` (e.g. service key `auth-1`, `container_name: auth-1`, `hostname: auth-1`).
- Per-instance env files live under `<module>/<module>-<N>.env` (e.g. `auth-service/auth-service-1.env`). The filename uses the **module name** so files stay grouped by service in the filesystem; the `INSTANCE_HOSTNAME` inside still uses the short alias.
- Log path resolves to `logs/<APP_NAME>/<INSTANCE_HOSTNAME>/<APP_NAME>__<INSTANCE_HOSTNAME>__<PROFILE>.log` (e.g. `logs/auth-service/auth-1/auth-service__auth-1__dev.log`).
- New services adopt the same pattern: aliases like `orders-1`, `inventory-1`, etc. — keep them short and unique across the cluster.

## Timezone

Single source of truth: `TZ` env var (defaults to `UTC` if missing). It propagates to:
- OS / JVM (system clock, default `TimeZone`, Logback timestamps, `*.now()` of zoned types)
- Hibernate `jdbc.time_zone`
- PostgreSQL session via Hikari `connection-init-sql: "SET TIME ZONE '${TZ:UTC}'"`

Entities use `java.time.Instant` (zone-agnostic; maps cleanly to `TIMESTAMPTZ`). The frontend localizes to user's timezone for display. **Do not** use `OffsetDateTime` / `LocalDateTime.now()` for stored timestamps — the offset of `OffsetDateTime.now()` reflects the JVM's TZ, not the event itself.

## Auth-service architecture

Package layout under `com.pharmaflow.auth_service`:

- `config.security` — `SecurityConfig` (stateless JWT chain), `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` (JSON 401/403), `AuthenticationEventListener` (publishes audit + drives `FailedAttemptService` on Spring Security events).
- `config.filter` — `RequestIdFilter` populates the `requestId` MDC from `X-Request-Id` header (or generates a UUID) and echoes it back. It is `Ordered.HIGHEST_PRECEDENCE`, so every downstream log line and audit record is correlated.
- `config.properties` — typed `@ConfigurationProperties` records (`JwtProperties`, `PasswordTokenProperties`, `SecurityPolicyProperties`). Registered in `AuthServiceApplication` via `@EnableConfigurationProperties`.
- `persistence.entity` / `persistence.repository` — JPA entities map the `iam` schema. Read-only entities extend `ReadOnlyRepository<T,ID>` (a `@NoRepositoryBean` base with only query methods). PostgreSQL `INET` columns use `@JdbcTypeCode(SqlTypes.INET)`.
- `service.interfaces` / `service.implementation` — one interface per service. `AuthService` is the orchestration façade; `RefreshTokenService`, `PasswordTokenService`, `AuditLogService`, `FailedAttemptService` are the building blocks.
- `presentation.controller` / `presentation.dto` / `presentation.advice` — `AuthController` exposes the public auth endpoints (`/auth/**`), `UserManagementController` exposes user-state operations like force-unlock (`/users/**`); `GlobalExceptionHandler` translates Spring Security and validation errors to JSON.

### Token model

- **Access token**: JWT (HMAC HS256), short TTL (`security.jwt.access-ttl-minutes`, default 15 min). Contains `userId`, `roles`, `permissions`, `type=access`.
- **Refresh token**: **opaque** random Base64Url string (32 bytes from `SecureRandom`). Only `SHA-256(token)` is persisted in `iam.refresh_token`. Each issuance has a `token_family` UUID; on rotation, the old row is marked revoked and `replaced_by_hash` points to the new row. If a revoked token is presented again it is treated as **reuse** and the entire family is revoked.
- **Refresh TTL — hybrid model (sliding + absolute)**:
    - `expiry_at` is the **sliding** window (`security.jwt.refresh-ttl-days`, default `1`). It is recalculated on every rotation, so an active user keeps renewing indefinitely within the absolute cap.
    - `absolute_expiry_at` is the **hard cap** (`security.jwt.refresh-absolute-ttl-days`, default `30`). It is set at the first issuance of a `token_family` and propagated unchanged on every rotation (the column is `updatable = false`). Once reached, the user must reauthenticate even if the sliding window is still open.
    - `persist()` always emits the new row with `expiry_at = min(now + slidingTtl, absoluteExpiryAt)` — so a rotated token never outlives the absolute cap.
    - `validateAndConsume()` checks the absolute first (message "Sesion expirada, reautenticacion requerida"), then the sliding (message "Refresh token expirado"). Both surface to the client as a generic 401 from `GlobalExceptionHandler`.
- **Password tokens**: opaque UUID-derived strings stored verbatim in `iam.password_token`. Two types: `SET_PASSWORD` (initial provisioning, long TTL) and `RESET_PASSWORD` (forgot-flow, short TTL). On consume, all refresh tokens for the user are revoked.

`JwtUtils` caches the `SecretKey` and `JwtParser` in `@PostConstruct` — don't recreate them per call. `TokenHasherUtils` provides `generateRawToken()` and `sha256Hex(...)`.

### Failed-attempt lockout & audit

`AuthenticationEventListener` subscribes to `AuthenticationSuccessEvent` and the various `AuthenticationFailure*Event`s. It delegates to `FailedAttemptService` (which uses `REQUIRES_NEW` so failure increments survive the login rollback) and to `AuditLogService` (`@Async` on a dedicated executor defined in `AsyncConfig`). `DaoAuthenticationProvider` is configured with `hideUserNotFoundExceptions=false` so the listener can tell "user doesn't exist" apart from "wrong password" — the client still sees the same generic 401 because `GlobalExceptionHandler` collapses both.

### Auth flows (step by step)

Every request passes through two ordered filters before hitting Spring Security:
1. **`RequestIdFilter`** (`HIGHEST_PRECEDENCE`) — reads `X-Request-Id` header (must be a valid UUID, otherwise generated), puts it in MDC under `requestId`, and echoes it back. Every log line and audit row for the request carries this ID.
2. **`JwtAuthenticationFilter`** — runs before `UsernamePasswordAuthenticationFilter`. Only acts when there is a `Bearer ` header AND no existing authentication in the context. Validates signature/issuer/exp via `JwtUtils.isTokenValid`, rejects tokens whose `type` claim is not `access`, then builds an `AuthenticatedPrincipal(userId, username)` and an authentication with `ROLE_*` + permission authorities pulled from the token claims. Never queries the DB on the hot path.

Endpoints in `/auth/**` are `permitAll`; everything else is `authenticated`. `@PreAuthorize` on controller methods is the place to add role-level restrictions on top — currently no controller pins to a specific role, so any authenticated user can call e.g. `POST /users/{id}/unlock`. Add `@PreAuthorize("hasRole('...')")` when business rules require it.

**`POST /auth/login` — `AuthServiceImpl.login`**
1. `FailedAttemptService.tryAutoUnlock(email)` — if the account was previously locked and `lockout-duration-minutes` has elapsed since `locked_at`, clear the lock and reset counter (audited as `ACCOUNT_UNLOCKED`). Runs in `REQUIRES_NEW`.
2. `AuthenticationManager.authenticate(email, password)` → `DaoAuthenticationProvider` → `UserDetailsServiceImpl.loadUserByUsername` (loads active roles + permissions) → BCrypt match. Throws `BadCredentialsException` / `LockedException` / `DisabledException` / `AccountExpiredException` / `CredentialsExpiredException` depending on the user state flags.
3. Spring publishes `AuthenticationSuccessEvent` or one of the `AuthenticationFailure*Event`s. `AuthenticationEventListener` handles them:
    - on success → `FailedAttemptService.onLoginSuccess` (resets counter, sets `last_login_at`) + audit `LOGIN`.
    - on bad credentials → if user exists, `FailedAttemptService.onLoginFailure` increments the counter and locks at `max-failed-attempts` (audit `ACCOUNT_LOCKED`). Always audit `LOGIN_FAILED`. The "user not found" branch is detected via `hideUserNotFoundExceptions=false` and does **not** increment any counter.
    - on locked/disabled/expired → audit `LOGIN_FAILED` with the specific reason.
4. Issue access JWT via `JwtUtils.generateAccessToken` (claims: `userId`, `roles`, `permissions`, `type=access`, `jti`, `iss`).
5. `RefreshTokenService.issueForUser` → new family UUID, `absolute_expiry_at = now + refreshAbsoluteTtlDays`, sliding `expiry_at = min(now + refreshTtlDays, absoluteExpiryAt)`. Persists hash + IP + UA.
6. Response: `ResponseLoginDto` with access token + raw refresh token.

**`POST /auth/refresh` — `AuthServiceImpl.refresh`**
1. `RefreshTokenService.validateAndConsume(raw)` — hashes the token, looks it up:
    - missing/blank → `BadCredentialsException`.
    - row not found → `BadCredentialsException`.
    - `revoked = true` → **reuse detected**: revoke the entire family, audit `REFRESH_TOKEN_REUSE`, throw `BadCredentialsException("Refresh token revocado")`.
    - `now > absolute_expiry_at` → `BadCredentialsException("Sesion expirada, reautenticacion requerida")`.
    - `now > expiry_at` (sliding) → `BadCredentialsException("Refresh token expirado")`.
2. Re-check user state on the row: `!active` or `accountLocked` or `accountExpired` → revoke **all** tokens for that user, audit `REFRESH_TOKEN` failure, throw `DisabledException`. Defends against the case where the user was disabled between issuance and refresh.
3. Reload `CustomUserDetails` from DB (roles/permissions may have changed since login) and generate a fresh access token.
4. `RefreshTokenService.rotate(current, ip, ua)` — emits a new token in the **same family** with the **same** `absolute_expiry_at`. Marks the current row `revoked = true`, sets `replaced_by_hash` to the new hash.
5. Audit `REFRESH_TOKEN` success. Response: `ResponseRefreshDto` with new access + new refresh.

**`POST /auth/logout` — `AuthServiceImpl.logout`**
1. `RefreshTokenService.revokeAndReturnUser(raw)` — hashes the token, if found and not yet revoked marks it `revoked = true` with `revoked_at = now`. Returns the user (or `null` if token not found).
2. If a user was returned, audit `LOGOUT`. Always returns 200 with "Sesion cerrada" — invalid/expired/unknown tokens do not 401 to avoid leaking which tokens exist.
3. **Only this token's row is revoked**, not the whole family — other devices keep their sessions.

**`POST /auth/forgot-password` — `AuthServiceImpl.forgotPassword`**
1. Look up user by email (case-insensitive). If not found or `!active`: log it server-side and return 200 with the same generic message anyway (no enumeration).
2. `PasswordTokenService.issueResetPasswordToken(user)` → invalidates any previous `RESET_PASSWORD` token for the user, generates a new opaque string (two UUIDs concatenated, no hyphens), persists with `expiry_at = now + reset-ttl-minutes`. Audit `RESET_PASSWORD`.
3. TODO: send email with link `/set-password?token=...`. Currently the token is only logged.

**`POST /auth/set-password` — `AuthServiceImpl.setPassword`** (used by both initial provisioning and forgot flow)
1. `PasswordTokenService.consumeAndChangePassword(token, newPassword)`:
    - blank → `BadCredentialsException`.
    - not found → `BadCredentialsException("Token invalido")`.
    - `used = true` → `BadCredentialsException("Token ya consumido")`.
    - `now > expiry_at` → `BadCredentialsException("Token expirado")`.
2. Update user: new BCrypt hash, `passwordChangedAt = now`, clear `forcePasswordChange` and `credentialsExpired`, reset `failed_attempts` to 0, unlock if locked.
3. Mark token `used = true` with `used_at = now`.
4. `RefreshTokenService.revokeAllForUser(userId)` — invalidates every session globally (forces re-login on all devices).
5. Audit `SET_PASSWORD` if token type was `SET_PASSWORD`, otherwise `PASSWORD_CHANGED`.

**`POST /users/{idUser}/unlock` — `UserManagementController.unlock`** (any authenticated caller; no role guard yet — add `@PreAuthorize` when the policy is decided)
1. `FailedAttemptService.forceUnlock(idUser, actorEmail)` — loads user (404 if missing), clears lock state and counter, audits `ACCOUNT_UNLOCKED` recording which actor performed the action (resolved from the JWT principal). `REQUIRES_NEW`.

**Authenticated request to any other endpoint**
1. `RequestIdFilter` sets MDC.
2. `JwtAuthenticationFilter` parses the Bearer token, validates signature/issuer/exp/type, builds authorities from the JWT claims (no DB call), sets the context.
3. `@PreAuthorize` (if present) checks roles/permissions.
4. Failures hit `RestAuthenticationEntryPoint` (401) or `RestAccessDeniedHandler` (403) — both emit a JSON body consistent with `GlobalExceptionHandler`.

### Flyway

Migrations live in `auth-service/src/main/resources/db/migration/V*__*.sql` and target schema `iam`. `ddl-auto=validate`. **Never edit a migration that has already been applied** — it changes the checksum and Flyway will refuse to start. If you must fix a bug in a past migration, add a new `Vn__*.sql` that ALTERs the schema. If a local DB has a checksum mismatch, run `flyway:repair` or drop and recreate the schema (local only).

## Conventions observed in this codebase

- **DTOs are Java records** (`presentation.dto.request` / `response`), validated with `jakarta.validation` annotations and `@Valid` in the controller.
- **`@RequiredArgsConstructor` + `final` fields** for dependency injection; no field injection.
- Entities use `@Builder.Default` whenever a Lombok `@Builder` is combined with a field default (otherwise the builder writes `null`).
- Commit messages: **Conventional Commits in Spanish** (e.g. `feat(auth-service): ...`, `fix: ...`).
