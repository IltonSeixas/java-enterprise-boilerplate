# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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

[Unreleased]: https://github.com/IltonSeixas/java-enterprise-boilerplate/compare/HEAD
