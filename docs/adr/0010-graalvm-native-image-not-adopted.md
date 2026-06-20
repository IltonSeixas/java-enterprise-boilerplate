# ADR-0010: GraalVM Native Image Not Adopted

**Date:** 2026-06-20
**Status:** Accepted

---

## Context

Spring Boot 4 ships first-class AOT (ahead-of-time) processing (`spring-boot:process-aot`) and integrates with the `org.graalvm.buildtools:native-maven-plugin` to produce a `native-image` binary — trading JIT startup time and baseline memory for a closed-world, reflection-free build. For a boilerplate positioned as production-grade, native-image is worth evaluating: faster cold starts and lower memory footprint matter for container-dense or serverless deployments.

The evaluation considered each dependency already on the classpath against GraalVM's closed-world requirement (every class accessed via reflection, proxying, or dynamic class loading must be known at build time, either via Spring's AOT hints or hand-written `reachability-metadata.json`):

- **`net.devh:grpc-server-spring-boot-starter` / `grpc-client-spring-boot-starter`** — a third-party starter, not part of `spring-boot-starter-parent`'s native-image-tested BOM. gRPC's Netty transport and protobuf message marshaling rely on reflection for descriptor resolution; the starter ships no native-image reachability metadata of its own, and Spring's AOT processor has no hints for it. Getting this working would mean hand-writing and maintaining reflect-config/proxy-config JSON against a dependency the project doesn't control.
- **`resilience4j-spring-boot3` + `spring-boot-starter-aspectj`** — `@CircuitBreaker`/`@Retry` (see [ADR-0008](0008-resilience4j-fault-tolerance.md)) are applied via Spring AOP proxies, with explicit aspect ordering load-bearing for correct failure-rate accounting. AOT processing generates proxy classes ahead of time, but AspectJ load-time/compile-time weaving interacts poorly with the AOT-generated `ApplicationContextInitializer`, since the proxy class graph needs to be fully resolved at build time, before the aspects are woven in. This is a known rough edge across the Spring ecosystem, not specific to this project's configuration.
- **`bcpkix-jdk18on` / `bcprov-jdk18on`** (BouncyCastle, used for Ed25519 PEM parsing — see [ADR-0005](0005-eddsa-jwt-signing.md)) — BouncyCastle's JCE provider registers algorithms via reflection and `Security.addProvider`, which needs explicit `--enable-all-security-services` plus provider-specific reachability metadata; achievable, but one more hand-maintained config surface.
- **Hibernate / Spring Data JPA, Flyway, Hikari** — reasonably well supported under Spring Boot 4's AOT hints, the least risky part of this stack.
- **springdoc-openapi-starter-webmvc-ui** — generates OpenAPI schema by reflecting over controller method signatures and DTOs at runtime; works in JVM mode but is dev/docs tooling, not something that needs to run in a minimal-footprint native binary in the first place.

No single blocker is unsolvable in isolation, but the combination — a third-party gRPC starter with zero native-image investment, plus AOP-based Resilience4j with non-trivial proxy/weaving interaction — means a working native build would require substantial hand-maintained reflection/proxy configuration that has to be re-verified on every dependency bump, with no upstream test coverage backing it.

## Decision

GraalVM Native Image / Spring AOT is **not adopted** in this boilerplate. No `native-maven-plugin`, no `-Pnative` profile, and no AOT processed-context plumbing are added to `pom.xml`.

The project remains a standard JVM (JIT) application, relying on Virtual Threads ([ADR-0004](0004-virtual-threads.md)) for throughput rather than native-image for startup/footprint.

## Consequences

**Positive:**
- No hand-maintained `reflect-config.json`/`proxy-config.json` to keep in sync with every dependency upgrade.
- Resilience4j's circuit-breaker/retry aspect ordering (ADR-0008) and gRPC's reflection-based marshaling keep working exactly as documented, with no AOT-specific carve-outs or behavioral differences between dev and "native" environments.
- Avoids a two-tier test matrix (JVM build + native build with possibly different behavior) for a boilerplate whose primary value is being a clear, faithful Clean Architecture/DDD reference.

**Negative:**
- Cold-start time and baseline memory remain JVM-typical, which matters less for a long-lived service (this boilerplate's target shape) than for a CLI tool or scale-to-zero function, but is still a real cost in container-dense deployments.
- A consumer of this boilerplate that specifically needs native-image (e.g., to run on a constrained edge device) must either replace the gRPC starter with one of the officially native-image-tested alternatives, drop gRPC entirely, or invest in the reachability metadata this ADR declines to write.

## Alternatives Considered

- **Full native-image support now** — rejected for this iteration: the cost (hand-written reachability metadata for a third-party gRPC starter, working around AspectJ/AOT interaction for Resilience4j) is large relative to the benefit for a reference boilerplate, and that metadata would need re-validation on every Spring Boot/gRPC/Resilience4j version bump.
- **Drop gRPC to unblock native-image** — rejected: the project's explicit requirement is a hybrid REST + gRPC API in the same project; removing gRPC to satisfy a build-target constraint would be the tail wagging the dog.
- **Partial native profile with documented broken features** — rejected: a build target that's known to silently misbehave for some of its endpoints (e.g., gRPC calls failing at runtime instead of build time) is worse than no native profile, since native-image failures often surface as opaque `ClassNotFoundException`/`NoSuchMethodError` at runtime rather than at build time.

## Revisit Conditions

This decision should be revisited if: (a) `net.devh`'s gRPC starter (or its upstream `io.grpc` dependencies) ships first-class GraalVM reachability metadata, (b) Spring's AOT processor gains documented, tested support for Resilience4j's AspectJ-based annotations, or (c) a concrete deployment target for this boilerplate specifically requires native-image's startup/memory profile, making the investment worth it for that use case.
