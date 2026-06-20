# ADR-0006: Enforce the Layering Rule with ArchUnit

**Date:** 2026-06-19  
**Status:** Accepted (the `application/`-may-use-DI-annotations exception described below was later removed — see [ADR-0007](0007-zero-spring-in-application-layer.md))

---

## Context

[ADR-0001](0001-clean-architecture.md) defines a strict inward-only dependency rule between `domain/`, `application/`, `infrastructure/`, and `interfaces/`. Until now this rule was enforced only by code review and the contributor's own discipline — nothing in the build actually fails if a future change introduces, say, a JPA import into `domain/`. Conventions enforced only by review erode the first time someone is in a hurry or unfamiliar with the codebase.

Auditing the existing code while writing this rule found that `application/` already depends on `org.springframework.stereotype.Service` and `org.springframework.beans.factory.annotation.Value` in every use case, for dependency injection. `domain/` has zero framework imports. The original ADR-0001 text ("never import Spring... from `domain/` or `application/`") did not match this reality.

## Decision

Add `archunit-junit5` as a test-scoped dependency and encode the layering rule as a real, automatically-run test: `LayeredArchitectureTest` (`src/test/java/.../architecture/`).

The rule distinguishes between two different concerns that the original ADR conflated:

1. **Dependency injection annotations** (`@Service`, `@Value`) — these are metadata read by the Spring container at startup; they do not pull persistence, transport, or security implementation details into the use case's logic. Allowed in `application/`.
2. **Concrete framework/library dependencies** (JPA/Hibernate, gRPC, JJWT, BouncyCastle, Redis client) — these couple business logic to a specific infrastructure choice. Forbidden in both `domain/` and `application/`.

`domain/` is held to the strictest standard: zero dependency on any of the packages above, including Spring entirely (not even DI annotations — domain objects are constructed directly with `new`, never injected).

Additional rules in the same test class:
- Layer access only flows inward (`interfaces/` and `infrastructure/` may depend on `application/` and `domain/`; the reverse is forbidden).
- Classes named `*UseCase` must live in `application.usecase`.
- Types in `application.port` must be interfaces, never concrete classes.

## Consequences

**Positive:**
- A pull request that violates the layering rule now fails `./mvnw test` instead of relying on a reviewer noticing an import.
- The rule's text is the rule — no drift between what ADR-0001 says and what the codebase actually does.

**Negative:**
- One more test dependency. ArchUnit adds non-trivial classpath-scanning time (single digit seconds) to the test run.
- The rule has to be precise about what "framework dependency" means — overly broad rules produce false positives on every code change; this ADR's distinction between DI annotations and concrete framework usage required deliberate scoping.

## Alternatives Considered

- **Keep enforcing via code review only** — what ADR-0001 originally specified; demonstrated to drift from reality once application/ adopted Spring DI annotations.
- **Custom checkstyle/PMD import rule** — possible, but ArchUnit expresses layered architecture rules (`layeredArchitecture()`, `mayOnlyBeAccessedByLayers`) far more directly than a generic import-restriction linter, and ships as a normal JUnit 5 test so it runs with the existing `./mvnw test` step with no extra CI wiring.
