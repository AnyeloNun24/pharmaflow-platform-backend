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
- `presentation.controller` / `presentation.dto` / `presentation.advice` — `AuthController` is the only controller; `GlobalExceptionHandler` translates Spring Security and validation errors to JSON.

### Token model

- **Access token**: JWT (HMAC HS256), short TTL (`security.jwt.access-ttl-minutes`, default 15 min). Contains `userId`, `roles`, `permissions`, `type=access`.
- **Refresh token**: **opaque** random Base64Url string (32 bytes from `SecureRandom`). Only `SHA-256(token)` is persisted in `iam.refresh_token`. Each issuance has a `token_family` UUID; on rotation, the old row is marked revoked and `replaced_by_hash` points to the new row. If a revoked token is presented again it is treated as **reuse** and the entire family is revoked.
- **Password tokens**: opaque UUID-derived strings stored verbatim in `iam.password_token`. Two types: `SET_PASSWORD` (initial provisioning, long TTL) and `RESET_PASSWORD` (forgot-flow, short TTL). On consume, all refresh tokens for the user are revoked.

`JwtUtils` caches the `SecretKey` and `JwtParser` in `@PostConstruct` — don't recreate them per call. `TokenHasherUtils` provides `generateRawToken()` and `sha256Hex(...)`.

### Failed-attempt lockout & audit

`AuthenticationEventListener` subscribes to `AuthenticationSuccessEvent` and the various `AuthenticationFailure*Event`s. It delegates to `FailedAttemptService` (which uses `REQUIRES_NEW` so failure increments survive the login rollback) and to `AuditLogService` (`@Async` on a dedicated executor defined in `AsyncConfig`). `DaoAuthenticationProvider` is configured with `hideUserNotFoundExceptions=false` so the listener can tell "user doesn't exist" apart from "wrong password" — the client still sees the same generic 401 because `GlobalExceptionHandler` collapses both.

### Flyway

Migrations live in `auth-service/src/main/resources/db/migration/V*__*.sql` and target schema `iam`. `ddl-auto=validate`. **Never edit a migration that has already been applied** — it changes the checksum and Flyway will refuse to start. If you must fix a bug in a past migration, add a new `Vn__*.sql` that ALTERs the schema. If a local DB has a checksum mismatch, run `flyway:repair` or drop and recreate the schema (local only).

## Conventions observed in this codebase

- **DTOs are Java records** (`presentation.dto.request` / `response`), validated with `jakarta.validation` annotations and `@Valid` in the controller.
- **`@RequiredArgsConstructor` + `final` fields** for dependency injection; no field injection.
- Entities use `@Builder.Default` whenever a Lombok `@Builder` is combined with a field default (otherwise the builder writes `null`).
- Commit messages: **Conventional Commits in Spanish** (e.g. `feat(auth-service): ...`, `fix: ...`).
