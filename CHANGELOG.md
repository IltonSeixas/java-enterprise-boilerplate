# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Initial project structure: Clean Architecture + DDD layers
- In-memory user repository adapter (zero-config default)
- Argon2id password hashing via Spring Security Crypto + BouncyCastle
- JWT RS256 access token + opaque refresh token with Redis rotation
- Spring MVC HTTP server with security filters (Bucket4j rate limiting, CORS, security headers)
- gRPC server via `grpc-spring-boot-starter`
- Micrometer tracing (OpenTelemetry bridge), Prometheus metrics, structured JSON logs via Logback
- PostgreSQL adapter via Spring Data JPA + Flyway migrations
- Virtual Threads (Project Loom) enabled globally
- ArchUnit tests enforcing Clean Architecture dependency rules
- Docker multi-stage image (Eclipse Temurin 21) and docker-compose stack
- GitHub Actions CI (compile, spotless, test, OWASP dependency-check), Docker, and Release workflows
- Architecture documentation, ADRs, security policy

[Unreleased]: https://github.com/IltonSeixas/java-enterprise-boilerplate/compare/HEAD
