# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [1.1.0] — 2026-07-01

### Security

- **CVE-2024-25710 / CVE-2024-26308** (`commons-compress`): forced `1.27.1` via `dependencyManagement` to override the transitive `1.24.0` pulled in by Testcontainers
- **CVE-2026-54515** (`jackson-databind`): forced `2.22.0` via `dependencyManagement` to override `2.21.4` pinned by the Spring Boot 4.1.0 BOM, which falls in the vulnerable range `>= 2.19.0, < 2.21.5`
- `GlobalExceptionHandler`: added handlers for `InvalidPasswordException` (→ 401) and `InvalidEmailException` (→ 400) — both previously fell through to the generic 500 handler
- `GrpcAuthenticationInterceptor`: changed from service-level allow-list to method-level deny-list for public endpoints — `gRPC Logout` now requires a valid Bearer access token, preventing an unauthenticated caller from supplying an arbitrary refresh token to the revocation endpoint
- `PostgresAuditLog`: fixed AOP proxy bypass — `@Transactional(REQUIRES_NEW)` was on a private `persist()` method called via `this`, which Spring's proxy never intercepted; merged into the public `record()` method so the transaction isolation is actually applied
- `PostgresUserRepository.saveFirstOwner`: catches `DataIntegrityViolationException` and re-throws as `UserAlreadyExistsException` — the race condition previously returned 500 when two registrations raced to claim the owner slot
- `InMemoryUserRepository.save`: enforces email uniqueness under lock, throwing `UserAlreadyExistsException` on duplicate — previously a duplicate email could silently overwrite an existing user record

### Changed

- `GrpcAuthenticationInterceptor`: public-method deny-list (`PUBLIC_METHODS`) replaces the previous service-level allow-list, ensuring new RPC methods are protected by default
- `AuthResponse`: `@JsonInclude(NON_NULL)` suppresses `refreshToken: null` from the JSON response body — the refresh token is delivered via `HttpOnly` cookie; the field was spuriously serialized as `null` by the `withoutRefreshToken()` factory
- `GET /api/v1/users`: query parameter renamed from `name` to `nameContains` to match the `ListUsersRequest` DTO field

### Fixed

- Audit log silently failing for all login/logout events: `V2__audit_log.sql` declared `target_user_id TEXT NOT NULL`, but the 3-arg `AuditEvent.of(type, actor, detail)` sets `target_user_id = null` — every login/logout audit write violated the constraint and was silently dropped. Column changed to nullable.
- `InMemoryUserRepository`: `saveFirstOwner` lock was named `firstOwnerLock` and only guarded `saveFirstOwner`; `save` could race concurrently. Renamed to `writeLock` and extended to both methods.
- `PostgresUserRepository.hasOwner`: replaced JPQL string literal `WHERE u.role = 'OWNER'` with the derived query `existsByRole(User.Role.OWNER)` — eliminates the silent mismatch risk if the enum value is renamed
- `V1__init.sql`: removed redundant `CREATE INDEX` on `email` — the `UNIQUE` constraint already creates an implicit index
- `RedisConfig`: removed manual `template.afterPropertiesSet()` call — Spring calls this automatically; the double invocation ran Redis initialization twice

### Added

- `AuditEventType.PROFILE_UPDATED`: profile name changes are now audited by `UpdateProfileUseCase`
- `InMemoryUserRepositoryTest`: two new tests covering email uniqueness guard and in-place update of an existing user
- `GlobalExceptionHandlerTest`: unit tests verifying the 401 and 400 mappings for `InvalidPasswordException` and `InvalidEmailException`
- `UpdateProfileUseCaseTest`: rewritten to inject `AuditPort` mock and verify that `PROFILE_UPDATED` is recorded on every successful profile update

---

## [1.0.0] — 2026-06-01

### Added
- Initial project structure: Clean Architecture + DDD layers
- In-memory user repository adapter (zero-config default)
- Argon2id password hashing computed directly via BouncyCastle's `Argon2BytesGenerator`, behind a `PasswordHasherPort` abstraction
- JWT access token (returned in the response body) + opaque refresh token delivered as an HttpOnly/Secure/SameSite=Strict cookie, rotated and stored server-side in Redis
- Spring MVC HTTP server with security filters (CORS allow-list, security headers, JWT authentication, in-process per-IP rate limiter on auth endpoints via `AuthRateLimitFilter`)
- gRPC server via `net.devh:grpc-spring-boot-starter`
- Micrometer tracing (OpenTelemetry OTLP bridge) and Prometheus metrics through Spring Boot Actuator
- PostgreSQL adapter via Spring Data JPA + Flyway migrations
- Virtual Threads (Project Loom) enabled globally
- Docker multi-stage image (Eclipse Temurin 21) and docker-compose stack
- GitHub Actions CI (compile, unit tests, OWASP dependency-check), Docker, and Release workflows
- Architecture documentation, ADRs, security policy
- Testcontainers-backed PostgreSQL integration test for the `PostgresUserRepository` adapter
- `LayeredArchitectureTest` (ArchUnit) enforcing the Clean Architecture dependency rule from ADR-0001 at build time — see [ADR-0006](docs/adr/0006-archunit-architecture-tests.md)
- Typed, `@Validated` `@ConfigurationProperties` records (`JwtProperties`, `SecurityProperties`, `RateLimitProperties`) replacing scattered `@Value("${...}")` injections, with fail-fast startup validation
- `infrastructure/config/UseCaseConfig` — the explicit composition root that wires every use case into the Spring container via `@Bean` factory methods
- Resilience4j circuit breakers (`postgres`, `redis`) and retries (`postgres-read`, `redis-read`) on the PostgreSQL and Redis adapters, with circuit-breaker-outer/retry-inner aspect ordering — see [ADR-0008](docs/adr/0008-resilience4j-fault-tolerance.md)
- Immutable domain audit log (`AuditEvent`/`AuditEventType`/`AuditPort`) recording registration, login (success and failure), password change, role change, logout, and token refresh, with in-memory and PostgreSQL adapters — see [ADR-0009](docs/adr/0009-domain-audit-log.md)
- `GET /api/v1/users` listing endpoint (Admin/Owner only) with role/active/name filtering and pagination, backed by Spring Data JPA `Specification` + `Pageable` in the PostgreSQL adapter, behind framework-agnostic `UserFilter`/`PageCriteria`/`UserPage` port types
- Custom Actuator health indicators: `jwtKeys` (JWT signing key pair readability, sharing the `Ed25519PemKeyLoader` used by `JwtTokenService`), `grpcServer` (embedded gRPC server liveness, also propagated to the standard `grpc.health.v1.Health` service via `HealthStatusManager`), and `auditLog` (Postgres audit log reachability, `postgres` profile only)
- Build metadata exposed at `/actuator/info` via the `spring-boot-maven-plugin` `build-info` goal
- ADR documenting the evaluation and decision not to adopt GraalVM Native Image / Spring AOT for this boilerplate — see [ADR-0010](docs/adr/0010-graalvm-native-image-not-adopted.md)

### Changed
- **Breaking:** JWT access tokens are now signed with EdDSA (Ed25519) instead of HS256. `JWT_SECRET` is replaced by `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` — see [ADR-0005](docs/adr/0005-eddsa-jwt-signing.md). Tokens issued under the previous version are not valid under this one.
- **Breaking (internal):** use cases under `application/usecase` are now plain classes with zero Spring annotations (`@Service`/`@Value` removed); they are instantiated exclusively through `UseCaseConfig`'s `@Bean` methods instead of component scanning. `LayeredArchitectureTest`'s rule was hardened to forbid any `org.springframework..` dependency in `application/`, with no DI-annotation exception — see [ADR-0007](docs/adr/0007-zero-spring-in-application-layer.md)

### Fixed
- Race condition (TOCTOU) in `PostgresUserRepository.saveFirstOwner()` — first-owner registration now relies on the `INSERT ... WHERE NOT EXISTS` row count instead of a separate existence check
- Confusing `UserAlreadyExistsException` message when the first-owner slot was already taken

[Unreleased]: https://github.com/IltonSeixas/java-enterprise-boilerplate/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/IltonSeixas/java-enterprise-boilerplate/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/IltonSeixas/java-enterprise-boilerplate/releases/tag/v1.0.0
