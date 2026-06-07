> **Work in progress** — this project is under active development and is not yet production-ready.

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

The `domain/` and `application/` packages never import from Spring, JPA, or any infrastructure library. This is enforced via ArchUnit tests in the CI pipeline.

---

## Stack

| Concern | Library |
|---|---|
| Framework | `Spring Boot 3.x` |
| Concurrency | `Project Loom` — Virtual Threads (JDK 21) |
| HTTP | `Spring MVC` (blocking, Tomcat with Virtual Threads) |
| gRPC | `grpc-server-spring-boot-starter` (`net.devh`) + `protobuf-maven-plugin` |
| Database (production) | `Spring Data JPA` + `Hibernate 6` + `Flyway` |
| Password hashing | `Spring Security Crypto` (Argon2id via BouncyCastle) |
| JWT | `nimbus-jose-jwt` |
| Validation | `Jakarta Bean Validation` + `Hibernate Validator` |
| Observability | `Micrometer` + `OpenTelemetry` + `Micrometer Tracing` |
| Structured logging | `Logback` + `logstash-logback-encoder` |
| Testing | `JUnit 5` + `Mockito` + `AssertJ` + `Testcontainers` |
| Architecture tests | `ArchUnit` |
| Build | `Gradle 8` (Kotlin DSL) |

---

## Getting Started

### Prerequisites

- Java 21+ (`sdk install java 21-tem` via SDKMAN)
- Optional for production: PostgreSQL 15+, Redis 7+

### Run immediately (in-memory, zero config)

```bash
git clone https://github.com/your-org/java-enterprise-boilerplate
cd java-enterprise-boilerplate
./gradlew bootRun
```

The server starts on `http://localhost:3000`. No database required.

### Run with PostgreSQL

```bash
cp src/main/resources/application.example.yml src/main/resources/application-local.yml
# Edit application-local.yml: set datasource, redis, jwt config

./gradlew bootRun --args='--spring.profiles.active=local,postgres'
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

- **Access token**: JWT signed with RS256 (asymmetric), TTL 15 min
- **Refresh token**: opaque UUID, stored in Redis with TTL 7 days, rotated on every use, delivered via HttpOnly cookie
- **Revocation**: evicting the Redis entry immediately invalidates the session
- **RBAC**: enforced via Spring Security method security (`@PreAuthorize`) at the use case boundary

### Security (Spring Security + custom filters)

- Rate limiting: Bucket4j sliding window per IP, backed by Redis
- Security headers: configured via `HttpSecurity` — CSP, HSTS, X-Frame-Options, X-Content-Type-Options
- CORS: explicit allow-list, never `*` in production
- Input validation: Jakarta Bean Validation on all DTOs — invalid input returns 400 before reaching the use case

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
| `GET` | `/actuator/health` | Health check (Spring Actuator) |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

### gRPC — `localhost:50051`

Proto definitions in `src/main/proto/boilerplate.proto`. Stubs are generated automatically at build time by the `protobuf-maven-plugin` — no manual step required. Each RPC mirrors a REST endpoint and delegates to the same use case classes, so business rules live in exactly one place.

| Service | RPC | Equivalent REST endpoint |
|---|---|---|
| `AuthService` | `Register` | `POST /api/v1/auth/register` |
| `AuthService` | `Login` | `POST /api/v1/auth/login` |
| `AuthService` | `RefreshToken` | `POST /api/v1/auth/refresh` |
| `AuthService` | `Logout` | `POST /api/v1/auth/logout` |
| `UserService` | `GetMe` | `GET /api/v1/users/me` |
| `UserService` | `GetUser` | — (privileged lookup of another user's profile) |
| `UserService` | `UpdateProfile` | `PATCH /api/v1/users/me` |
| `UserService` | `ChangePassword` | `POST /api/v1/users/me/password` |

- **Authentication**: `UserService` calls require an `authorization: Bearer <access-token>` request metadata entry. A global server interceptor validates the token, confirms the account is active, and exposes the caller through a `Context` key — mirroring the REST `JwtAuthenticationFilter`'s active-account check.
- **Error mapping**: domain exceptions are translated to gRPC status codes (`INVALID_ARGUMENT`, `ALREADY_EXISTS`, `NOT_FOUND`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INTERNAL`) so clients receive the same semantics as REST responses, expressed idiomatically for gRPC.
- **Reflection**: `grpc-services` exposes server reflection, so the API can be explored with `grpcurl` or any reflection-aware client without shipping `.proto` files separately.

---

## Testing

```bash
./gradlew test                    # unit tests (no external deps, uses in-memory adapter)
./gradlew test -Pintegration      # integration tests (Testcontainers — requires Docker)
./gradlew jacocoTestReport        # coverage report (target: 80%+ on domain + application)
```

### Structure

- **Unit tests**: `src/test/java/**/*Test.java`. Domain entities, value objects, and use cases tested with JUnit 5 and Mockito. Repository mocks are generated from port interfaces — no Spring context needed.
- **Integration tests**: `src/test/java/**/*IT.java`. Full Spring context with Testcontainers (PostgreSQL, Redis) spun up automatically.
- **Architecture tests**: `src/test/java/**/ArchitectureTest.java`. ArchUnit rules enforce the dependency rule at compile/test time — the build fails if any domain class imports a Spring annotation.

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

---

## Configuration

All configuration via `application.yml` and environment variable overrides (Spring's relaxed binding).

| Property / Env Var | Default | Description |
|---|---|---|
| `SERVER_PORT` | `3000` | HTTP port |
| `GRPC_PORT` | `50051` | gRPC port |
| `SPRING_DATASOURCE_URL` | — | JDBC PostgreSQL URL |
| `SPRING_DATA_REDIS_URL` | — | Redis URL |
| `JWT_SECRET` | — | RSA private key path or inline PEM |
| `JWT_ACCESS_TTL` | `900` | Access token TTL (seconds) |
| `JWT_REFRESH_TTL` | `604800` | Refresh token TTL (seconds) |
| `RATE_LIMIT_RPS` | `100` | Max requests/sec per IP |
| `ADAPTER` | `memory` | Persistence adapter: `memory` or `postgres` |

---

## Docker

```bash
# Multi-stage build — GraalVM Native Image option available
docker build -t java-enterprise-boilerplate .

docker run -p 3000:3000 -p 50051:50051 --env-file .env java-enterprise-boilerplate
```

```bash
# Full stack: app + postgres + redis + jaeger
docker compose up
```

### Native Image (optional)

Compile to a native binary with GraalVM for sub-100ms startup:

```bash
./gradlew nativeCompile
./build/native/nativeCompile/boilerplate
```

---

## CI/CD

GitHub Actions pipelines in `.github/workflows/`:

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | push / PR | compile, test (unit + arch), dependency-check |
| `docker.yml` | push to `main` | build + push to GHCR |
| `release.yml` | tag `v*` | build JAR + native image, create GitHub Release |

OWASP Dependency-Check runs on every push to detect known CVEs in Maven/Gradle dependencies.

---

## Plugging in a Real Database

Implement the `UserRepository` interface from `domain/repository/` and annotate your adapter with `@Profile("postgres")`. The in-memory adapter is `@Profile("default")`. Switch profiles via `SPRING_PROFILES_ACTIVE=postgres`.

Flyway migrations live in `src/main/resources/db/migration/` — run automatically on startup when the `postgres` profile is active.

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
