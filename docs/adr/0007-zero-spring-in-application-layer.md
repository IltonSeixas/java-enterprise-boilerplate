# ADR-0007: Remove Spring Entirely from the Application Layer

**Date:** 2026-06-20
**Status:** Accepted
**Supersedes:** Part of [ADR-0001](0001-clean-architecture.md) and [ADR-0006](0006-archunit-architecture-tests.md) — specifically, the exception that allowed `@Service`/`@Value` in `application/`

---

## Context

ADR-0001 and ADR-0006 allowed `application/` to depend on Spring's dependency-injection annotations (`@Service`, `@Value`), reasoning that DI metadata does not pull a concrete persistence, transport, or security implementation into the use case. In practice this exception kept a live import of `org.springframework.*` inside every use case class and made `application/` not fully framework-agnostic — a use case could not be instantiated, tested, or reused outside a Spring context without that import being present, even if unused at runtime.

A financial-institution-grade core should be provable as framework-agnostic by inspection: zero `org.springframework` (or any other framework) import anywhere under `application/`, full stop, with no DI-annotation carve-out.

## Decision

Use cases are now plain Java classes with a constructor and an `execute(...)` method — no Spring annotation of any kind. They are wired into the Spring container exclusively through explicit `@Bean` factory methods in `infrastructure/config/UseCaseConfig`, which call the use case constructors directly. Spring continues to manage the resulting beans normally (same container, same injection into controllers and gRPC services); only the *registration mechanism* changes from implicit component-scan (`@Service`) to explicit factory wiring (`@Bean`).

Configuration values previously injected via `@Value("${...}")` directly into a use case constructor parameter are now sourced from typed, `@Validated` `@ConfigurationProperties` records (e.g. `JwtProperties`, `SecurityProperties`, `RateLimitProperties`) defined in a neutral top-level `config.properties` package — a sibling of `domain/`, `application/`, `infrastructure/`, and `interfaces/`, not nested under any of the four layers. This placement is deliberate: the existing `layeredArchitecture()` ArchUnit rule forbids `interfaces/` classes from depending on `infrastructure/` classes, and some property records (e.g. `RateLimitProperties`, consumed by an `interfaces/filter` class) must be consumable from both sides of that boundary.

`LayeredArchitectureTest`'s rule was hardened accordingly: `noClasses().that().resideInAPackage(APPLICATION)` may no longer depend on `org.springframework..` at all (the prior rule excluded it from the forbidden-package list with the comment "Spring is allowed here only for dependency injection"; that exception is now removed).

## Consequences

**Positive:**
- `application/` is verifiably zero-dependency on any framework, not just persistence/transport frameworks — a stronger, simpler invariant than the previous "DI annotations are fine, concrete adapters are not" distinction.
- Use cases can be constructed with `new` in a unit test, a CLI tool, or a future non-Spring entry point without any classpath requirement beyond the JDK and domain code.
- The composition root (`UseCaseConfig`) makes every use case's dependency graph explicit and readable in one file, rather than implicit via component scanning.

**Negative:**
- Adding a new use case now requires a corresponding `@Bean` method in `UseCaseConfig` — one extra, deliberate step compared to dropping a `@Service` annotation on the class.
- `@ConfigurationProperties` records live outside the four-layer model in a neutral `config` package, which is a structural exception to the otherwise strict layering — documented here so it isn't mistaken for layering drift.

## Alternatives Considered

- **Keep the ADR-0006 DI-annotation exception** — simpler, but leaves a live Spring import in every use case and a weaker, harder-to-explain rule ("annotations are fine, concrete classes are not").
- **Place `@ConfigurationProperties` records under `infrastructure/config/properties`** — tried first; rejected because it violates the `layeredArchitecture()` rule the moment an `interfaces/`-layer class (e.g. a servlet filter) needs to read a property value, since `Interfaces` may not depend on `Infrastructure`.
