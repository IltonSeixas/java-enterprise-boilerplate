# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Initial project structure: Clean Architecture + DDD layers
- In-memory user repository adapter (zero-config default)
- Argon2id password hashing computed directly via BouncyCastle's `Argon2BytesGenerator`, behind a `PasswordHasherPort` abstraction
- JWT HS256 access token (returned in the response body) + opaque refresh token delivered as an HttpOnly/Secure/SameSite=Strict cookie, rotated and stored server-side in Redis
- Spring MVC HTTP server with security filters (CORS allow-list, security headers, JWT authentication, in-process per-IP rate limiter on auth endpoints via `AuthRateLimitFilter`)
- gRPC server via `net.devh:grpc-spring-boot-starter`
- Micrometer tracing (OpenTelemetry OTLP bridge) and Prometheus metrics through Spring Boot Actuator
- PostgreSQL adapter via Spring Data JPA + Flyway migrations
- Virtual Threads (Project Loom) enabled globally
- Docker multi-stage image (Eclipse Temurin 21) and docker-compose stack
- GitHub Actions CI (compile, unit tests, OWASP dependency-check), Docker, and Release workflows
- Architecture documentation, ADRs, security policy
- `LayeredArchitectureTest` (ArchUnit) enforcing the Clean Architecture dependency rule from ADR-0001 at build time — see [ADR-0006](docs/adr/0006-archunit-architecture-tests.md)

[Unreleased]: https://github.com/IltonSeixas/java-enterprise-boilerplate/compare/HEAD
