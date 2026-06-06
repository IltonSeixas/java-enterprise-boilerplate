# ADR-0004: Enable Project Loom Virtual Threads

**Date:** 2026-06-06  
**Status:** Accepted

---

## Context

Java backend services have historically required reactive programming (WebFlux, RxJava) to handle high concurrency without exhausting platform threads. Reactive code is harder to write, debug, and reason about — and it leaks into every layer, including the domain.

Java 21 delivers **Virtual Threads** (JEP 444) as a GA feature.

## Decision

Enable Virtual Threads globally via:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Spring Boot 3.2+ maps this to Tomcat's thread pool automatically.

## Consequences

**Positive:**
- Blocking I/O (JDBC, Redis) is cheap — the JVM unmounts the virtual thread while waiting, freeing the carrier thread.
- Throughput comparable to reactive stacks without reactive programming model.
- The domain and application layers remain purely imperative — no `Mono`, `Flux`, `CompletableFuture` in business logic.
- Standard stack traces — debugging and profiling work with existing tools.
- `@Transactional` and JDBC work without adaptation.

**Negative:**
- Virtual threads pin to carrier threads when holding a synchronized monitor — avoid `synchronized` blocks in hot paths (use `ReentrantLock` instead). Spring and most libraries have been updated to avoid pinning.
- Not beneficial for CPU-bound work — Virtual Threads are an I/O concurrency primitive, not a parallelism tool.

## Alternatives Considered

- **Spring WebFlux (Project Reactor)** — high throughput but reactive model propagates into every layer, including the domain. Eliminated because it violates the domain isolation requirement.
- **Platform threads only** — safe but limits concurrency under I/O load without manual tuning of thread pool sizes.
