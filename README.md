# java-enterprise-boilerplate

[![CI](https://github.com/IltonSeixas/java-enterprise-boilerplate/actions/workflows/ci.yml/badge.svg)](https://github.com/IltonSeixas/java-enterprise-boilerplate/actions/workflows/ci.yml)
[![Docker](https://github.com/IltonSeixas/java-enterprise-boilerplate/actions/workflows/docker.yml/badge.svg)](https://github.com/IltonSeixas/java-enterprise-boilerplate/actions/workflows/docker.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)

Production-ready enterprise backend boilerplate in **Java 21** — built on Clean Architecture, Domain-Driven Design, and Test-Driven Development. Leverages Project Loom Virtual Threads for high-throughput concurrency without reactive complexity. Runs immediately with an in-memory adapter; plug in PostgreSQL when ready for production.

---

## Philosophy

Java 21 changes the concurrency model fundamentally: Virtual Threads make blocking I/O cheap enough that reactive frameworks are no longer a prerequisite for high throughput. This boilerplate embraces that — write straightforward imperative code, get concurrency for free. The domain model uses Records and sealed interfaces where appropriate, keeping the core immutable and expressive without ceremony.

---

## Architecture

```
src/main/java/com/enterprise/boilerplate/
│
├── domain/               # Enterprise business rules — zero framework deps
│   ├── entity/           # Aggregates and Entities (Java Records where applicable)
│   ├── valueobject/      # Immutable, self-validating values (sealed classes / records)
│   ├── repository/       # Port interfaces
│   └── exception/        # Domain exception hierarchy
│
├── application/          # Use cases — depends only on domain
│   ├── usecase/          # One class per use case
│   ├── port/
│   │   ├── in/           # Input port interfaces (commands / queries)
│   │   └── out/          # Output port interfaces
│   └── dto/              # Record-based DTOs
│
├── infrastructure/       # Adapters — implements domain ports
│   ├── persistence/
│   │   ├── memory/       # Default: zero-config, runs immediately
│   │   └── postgres/     # Production: Spring Data JPA + Flyway
│   ├── security/         # Argon2id via Spring Security Crypto
│   ├── cache/            # Redis adapter (Spring Data Redis)
│   └── telemetry/        # Micrometer + OpenTelemetry config
│
└── interfaces/           # Entry points
    ├── rest/             # Spring MVC controllers, exception handlers
    └── grpc/             # gRPC service implementations, auth interceptor, error mapping
```

### Dependency rule

```
interfaces/ → application/ → domain/
infrastructure/ → application/ → domain/
```

The `domain/` and `application/` packages never import from Spring, JPA, or any infrastructure library. This boundary is enforced automatically at build time by `LayeredArchitectureTest` (ArchUnit) — see [ADR-0006](docs/adr/0006-archunit-architecture-tests.md) and [contributing.md](docs/contributing.md).

---

## Stack

| Concern | Library |
|---|---|
| Framework | `Spring Boot 4.x` |
| Concurrency | `Project Loom` — Virtual Threads (JDK 21) |
| HTTP | `Spring MVC` (blocking, Tomcat with Virtual Threads) |
| gRPC | `grpc-server-spring-boot-starter` (`net.devh`) + `protobuf-maven-plugin` |
| Database (production) | `Spring Data JPA` + `Hibernate 6` + `Flyway` |
| Password hashing | `Spring Security Crypto` (Argon2id via BouncyCastle) |
| JWT | `jjwt` (`io.jsonwebtoken`) |
| Validation | `Jakarta Bean Validation` + `Hibernate Validator` |
| Observability | `Micrometer` + `OpenTelemetry` + `Micrometer Tracing` |
| Structured logging | `Logback` |
| Testing | `JUnit 5` + `Mockito` + `AssertJ` + `ArchUnit` |
| Build | `Maven` (with `mvnw` wrapper) |

---

## Getting Started

### Prerequisites

- Java 21+ (`sdk install java 21-tem` via SDKMAN)
- Optional for production: PostgreSQL 15+, Redis 7+

### Run immediately (in-memory, zero config)

```bash
git clone https://github.com/your-org/java-enterprise-boilerplate
cd java-enterprise-boilerplate
openssl genpkey -algorithm ed25519 -out jwt_private.pem
openssl pkey -in jwt_private.pem -pubout -out jwt_public.pem
./mvnw spring-boot:run
```

The server starts on `http://localhost:3000`. No database required.

### Run with PostgreSQL

```bash
cp .env.example .env
# Edit .env: set SPRING_DATASOURCE_*, REDIS_*, JWT_PRIVATE_KEY_PATH, JWT_PUBLIC_KEY_PATH, etc.

SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

---

## Security

### Password Hashing — Argon2id

Passwords are hashed with **Argon2id** via `Spring Security Crypto` backed by **BouncyCastle**. The `Argon2PasswordEncoder` is pre-configured with OWASP-recommended parameters:

- Memory: 65536 KB (64 MB)
- Iterations: 3
- Parallelism: 4

The `PasswordHasher` output port in `application/port/out/` abstracts the algorithm — the domain never touches crypto directly.

### Authentication Flow

- **Access token**: JWT EdDSA (Ed25519), TTL 15 min, validated on every authenticated request
- **Refresh token**: opaque UUID, stored in Redis with TTL 7 days, rotated on every use, delivered via HttpOnly cookie
- **Revocation**: evicting the Redis entry immediately invalidates the session
- **RBAC**: enforced via Spring Security method security (`@PreAuthorize`) at the use case boundary

### Security (Spring Security + custom filters)

- Security headers: configured via `HttpSecurity` — CSP, HSTS, X-Frame-Options, X-Content-Type-Options
- CORS: explicit allow-list, never `*` in production
- Input validation: Jakarta Bean Validation on all DTOs — invalid input returns 400 before reaching the use case
- CSRF: stateless endpoints authenticate via the `Authorization` bearer header and are exempt. `/api/v1/auth/refresh` and `/api/v1/auth/logout` authenticate via the `refresh_token` cookie and require the `X-XSRF-TOKEN` header, read from the `XSRF-TOKEN` cookie set by the server

### Audit Logging

Every identity- and access-sensitive use case (registration, login success/failure, password change, role change, profile update, logout, token refresh) records an immutable `AuditEvent` through the `AuditPort` output port — see [ADR-0009](docs/adr/0009-domain-audit-log.md). The in-memory adapter is the zero-config default; the PostgreSQL adapter persists to a dedicated `audit_log` table and never fails the use case it observes, degrading gracefully if the audit store itself is unavailable.

---

## API

### REST — `http://localhost:3000`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new user |
| `POST` | `/api/v1/auth/login` | Authenticate, receive tokens |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token |
| `GET` | `/api/v1/users/me` | Get authenticated user profile |
| `PUT` | `/api/v1/users/me` | Update authenticated user profile |
| `PUT` | `/api/v1/users/me/password` | Change authenticated user password |
| `GET` | `/api/v1/users/{id}` | Get a user by id |
| `GET` | `/api/v1/users` | List users with filtering and pagination (Admin/Owner only) |
| `PUT` | `/api/v1/users/{id}/role` | Change a user's role (Owner only, cannot change own role) |
| `GET` | `/actuator/health` | Health check (Spring Actuator) |
| `GET` | `/actuator/info` | Build metadata (artifact, version, build time) — requires authentication |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

`GET /api/v1/users` accepts `role`, `active`, `nameContains`, `page` (default `0`), `size` (default `20`, max `100`), `sortBy` (default `createdAt`; allowed values: `createdAt`, `name`, `email`, `role`) and `direction` (`ASC` or `DESC`, default `ASC`) query parameters. The `sortBy` value is validated against an explicit allowlist before reaching the persistence adapter — unknown fields are rejected with `400 Bad Request`. Filtering, pagination, and sorting are translated to a JPA `Specification`/`Pageable` at the persistence adapter; the domain and application layers only see framework-agnostic `UserFilter`/`PageCriteria`/`UserPage` types.

`/actuator/health` aggregates the auto-configured `db`, `redis`, `diskSpace` and `ssl` indicators with three custom ones: `jwtKeys` (JWT signing key pair readability), `grpcServer` (embedded gRPC server liveness, also propagated to the standard `grpc.health.v1.Health` service so `grpc_health_probe` and `/actuator/health` always agree) and `auditLog` (Postgres audit log reachability, `postgres` profile only). See [docs/observability.md](docs/observability.md#health-checks) for details.

### gRPC — `localhost:50051`

Proto definitions in `src/main/proto/boilerplate.proto`. Stubs are generated automatically at build time by the `protobuf-maven-plugin` — no manual step required. Each RPC mirrors a REST endpoint and delegates to the same use case classes, so business rules live in exactly one place.

| Service | RPC | Equivalent REST endpoint |
|---|---|---|
| `AuthService` | `Register` | `POST /api/v1/auth/register` |
| `AuthService` | `Login` | `POST /api/v1/auth/login` |
| `AuthService` | `RefreshToken` | `POST /api/v1/auth/refresh` |
| `AuthService` | `Logout` | `POST /api/v1/auth/logout` |
| `UserService` | `GetMe` | `GET /api/v1/users/me` |
| `UserService` | `GetUser` | `GET /api/v1/users/{id}` |
| `UserService` | `UpdateProfile` | `PUT /api/v1/users/me` |
| `UserService` | `ChangePassword` | `PUT /api/v1/users/me/password` |
| `UserService` | `ChangeRole` | `PUT /api/v1/users/{id}/role` |

- **Authentication**: `UserService` calls require an `authorization: Bearer <access-token>` request metadata entry. A global server interceptor validates the token, confirms the account is active, and exposes the caller through a `Context` key — mirroring the REST `JwtAuthenticationFilter`'s active-account check.
- **Error mapping**: domain exceptions are translated to gRPC status codes (`INVALID_ARGUMENT`, `ALREADY_EXISTS`, `NOT_FOUND`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INTERNAL`) so clients receive the same semantics as REST responses, expressed idiomatically for gRPC.
- **Reflection**: `grpc-services` exposes server reflection, so the API can be explored with `grpcurl` or any reflection-aware client without shipping `.proto` files separately.

---

## Testing

```bash
./mvnw test                                   # unit tests (no external deps, uses in-memory adapter)
./mvnw test -Dgroups=integration -DexcludedGroups=  # integration tests (boots the real gRPC server)
```

### Structure

- **Unit tests**: `src/test/java/**/*Test.java`. Domain entities, value objects, and use cases tested with JUnit 5, Mockito, and AssertJ. Repository test doubles are plain Java classes implementing the port interface — no Spring context needed.
- **Integration tests**: tagged with `@Tag("integration")` (e.g. `GrpcServerIntegrationTest`) and excluded from the default `mvn test` run via the Surefire `excludedGroups` configuration in `pom.xml`. They boot the real gRPC server wired with in-memory adapters and drive it through actual gRPC clients — no external infrastructure required.
- **Architecture tests**: `LayeredArchitectureTest` (ArchUnit) enforces the Clean Architecture dependency rule from [ADR-0001](docs/adr/0001-clean-architecture.md) at build time — see [ADR-0006](docs/adr/0006-archunit-architecture-tests.md). Runs as part of the regular `./mvnw test` step.

### TDD Approach

Use cases are written test-first with Mockito mocks for all output ports. The `@SpringBootTest` context is reserved for integration tests — unit tests are pure JUnit 5, start in milliseconds, and never touch the filesystem.

---

## Virtual Threads

Virtual Threads (Project Loom, JDK 21) are enabled globally:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Tomcat uses Virtual Threads for request handling. Each request gets its own lightweight thread — blocking JDBC and Redis calls are cheap. No reactive types, no callback hell, no `Mono`/`Flux` in the application layer.

---

## Observability

- **Traces**: Micrometer Tracing with the OpenTelemetry bridge — every HTTP request and gRPC call is a span; use cases emit child spans via `Observation`
- **Metrics**: Micrometer with Prometheus registry, exposed via Spring Actuator at `/actuator/prometheus`
- **Logs**: structured JSON via Logback + `logstash-logback-encoder`, correlated with trace/span IDs automatically

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

### Resilience

The PostgreSQL and Redis adapters are each wrapped in a dedicated Resilience4j circuit breaker (`postgres`, `redis`), with retry applied only to read paths (`postgres-read`, `redis-read`) — never to writes, to avoid double-applying a write whose response was lost in transit. The circuit breaker is configured as the outer aspect and retry as the inner one, so a transient failure that succeeds on retry counts as a single success against the breaker's failure rate — see [ADR-0008](docs/adr/0008-resilience4j-fault-tolerance.md).

---

## Configuration

All configuration via `application.yml` and environment variable overrides (Spring's relaxed binding).

| Property / Env Var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `3000` | HTTP port |
| `GRPC_PORT` | `50051` | gRPC port |
| `SPRING_PROFILES_ACTIVE` | `inmemory` | Persistence profile: `inmemory` or `postgres` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/boilerplate` | JDBC PostgreSQL URL — only read on the `postgres` profile |
| `DB_POOL_SIZE` | `10` | HikariCP maximum pool size — only read on the `postgres` profile |
| `DB_POOL_MIN_IDLE` | `2` | HikariCP minimum idle connections — only read on the `postgres` profile |
| `DB_POOL_CONNECT_TIMEOUT_MS` | `30000` | HikariCP connection acquisition timeout (ms) — only read on the `postgres` profile |
| `DB_POOL_IDLE_TIMEOUT_MS` | `600000` | HikariCP idle connection timeout (ms) — only read on the `postgres` profile |
| `DB_POOL_MAX_LIFETIME_MS` | `1800000` | HikariCP maximum connection lifetime (ms) — only read on the `postgres` profile |
| `SPRING_DATA_REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `REDIS_CONNECT_TIMEOUT_MS` | `2000` | Redis (Lettuce) connection timeout (ms) |
| `REDIS_COMMAND_TIMEOUT_MS` | `2000` | Redis (Lettuce) command timeout (ms) |
| `JWT_PRIVATE_KEY_PATH` | `jwt_private.pem` | Path to the Ed25519 PEM private key used to sign access tokens |
| `JWT_PUBLIC_KEY_PATH` | `jwt_public.pem` | Path to the Ed25519 PEM public key used to verify access tokens |
| `JWT_ACCESS_EXPIRY_MINUTES` | `15` | Access token TTL (minutes) |
| `JWT_REFRESH_EXPIRY_DAYS` | `7` | Refresh token TTL (days) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated CORS allow-list |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP HTTP traces endpoint (Spring Boot Actuator) |

---

## Docker

```bash
# Multi-stage build — Maven build stage + slim JRE Alpine runtime image
docker build -t java-enterprise-boilerplate .

docker run -p 3000:3000 -p 50051:50051 --env-file .env java-enterprise-boilerplate
```

```bash
# Full stack: app + postgres + redis + jaeger
docker compose up
```

---

## CI/CD

GitHub Actions pipelines in `.github/workflows/`:

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | push / PR | compile, unit tests, dependency vulnerability check |
| `docker.yml` | push to `main` | build + push to GHCR |
| `release.yml` | tag `v*` | build JAR, create GitHub Release, build + push release image |

The OWASP Dependency-Check Maven plugin runs on every push to detect known CVEs in dependencies.

---

## Plugging in a Real Database

Implement the `UserRepository` interface from `domain/repository/` and annotate your adapter with `@Profile("postgres")`. The in-memory adapter is `@Profile("inmemory")` (the active profile by default — see `SPRING_PROFILES_ACTIVE`). Switch profiles via `SPRING_PROFILES_ACTIVE=postgres`.

Place Flyway migration scripts (`V1__init.sql`, `V2__...`, etc.) in `src/main/resources/db/migration/` — Flyway runs them automatically on startup when the `postgres` profile is active.

---

## Author

**Ilton Seixas** — [contact@iltonseixas.com](mailto:contact@iltonseixas.com)

---

## Disclaimer

This boilerplate is provided **as-is**, for educational and reference purposes only.

**No warranty.** The author makes no representations or warranties of any kind, express or implied, regarding the correctness, completeness, reliability, suitability, or availability of this software for any purpose. Your use of this code is entirely at your own risk.

**No liability.** To the fullest extent permitted by applicable law, the author shall not be held liable for any direct, indirect, incidental, special, consequential, or punitive damages arising from the use or misuse of this software — including but not limited to data breaches, security incidents, financial loss, service downtime, or regulatory non-compliance.

**Misuse.** The author is not responsible for any unlawful, harmful, or unethical use of this codebase by any party.

**Security.** Security patterns and cryptographic implementations in this project follow industry best practices at the time of writing. However, the threat landscape evolves. You are solely responsible for auditing, hardening, and maintaining any system you build on top of this code.

> **Never blindly trust third-party code — including this project.**
> The author strongly recommends that you read and understand every line before deploying to production. Security-sensitive components (authentication, password hashing, token management, input validation) deserve particular scrutiny. No code review by a stranger on the internet replaces your own.

---

## License

MIT — Copyright (c) Ilton Seixas
