# ADR-0001: Adopt Clean Architecture with Hexagonal Ports & Adapters

**Date:** 2026-06-06  
**Status:** Accepted

---

## Context

Spring Boot applications commonly suffer from architecture erosion: JPA entities leak into controllers, business logic accumulates in `@Service` classes that directly import Spring Data repositories, and testing requires a full application context. A boilerplate must demonstrate the discipline to prevent this.

## Decision

The project adopts **Clean Architecture** in its Hexagonal / Ports & Adapters form, with four layers and a strict inward-only dependency rule:

1. **domain/** — entities, value objects, repository interfaces. Zero Spring imports.
2. **application/** — use cases, input/output port interfaces. Imports domain only.
3. **infrastructure/** — adapters (JPA, Redis, BouncyCastle). Implements application ports.
4. **interfaces/** — Spring MVC controllers, gRPC services. Calls application use cases.

The boundary is enforced automatically by `LayeredArchitectureTest` (ArchUnit, see [ADR-0006](0006-archunit-architecture-tests.md)), not just code review:

- `domain/` may not depend on any framework — no Spring, JPA, Hibernate, gRPC, JJWT, BouncyCastle, or Redis client classes.
- `application/` may use Spring's dependency-injection annotations (`@Service`, `@Value`) to stay wireable as a singleton bean, but may not depend on persistence (JPA/Hibernate), transport (gRPC), or concrete security (JJWT, BouncyCastle, Redis) libraries.

## Consequences

**Positive:**
- Use cases are testable with Mockito mocks — no `@SpringBootTest` context required; tests start in milliseconds.
- JPA entities are separate from domain entities — persistence concerns do not pollute the domain model.
- The dependency rule is a compiled, automatically-enforced test rather than a convention that erodes silently over time.

**Negative:**
- Two entity classes (domain + JPA) per aggregate — more code to maintain.
- Spring DI is used for lifecycle management but must not be used as a coupling mechanism inside the domain.

## Alternatives Considered

- **Anemic domain model with fat services** — the Spring default pattern; leads to procedural code in `@Service` classes with no domain invariants.
- **NestJS-style modules within Spring** — not idiomatic Java.
