# ADR-0008: Resilience4j Circuit Breakers and Retries on Infrastructure Adapters

**Date:** 2026-06-20
**Status:** Accepted

---

## Context

`PostgresUserRepository` and `RedisTokenStore` are the two adapters on the critical path of every authenticated request — a slow or partially failing PostgreSQL or Redis instance must not be allowed to exhaust application threads or cascade into a full outage. A financial-institution-grade system needs an explicit, configured fault-tolerance boundary at each adapter, not an implicit reliance on connection-pool timeouts alone.

The application already separates read paths (tolerant of a short retry) from write paths (must not be retried blindly, since a retried write can double-apply if the first attempt actually succeeded server-side but the response was lost).

## Decision

Resilience4j is applied directly on the infrastructure adapters via annotations (`@CircuitBreaker`, `@Retry`), never inside `application/` — consistent with [ADR-0007](0007-zero-spring-in-application-layer.md)'s zero-Spring-in-`application/` rule. Two independent circuit breaker instances are configured in `application.yml`, one per external dependency (`postgres`, `redis`), each with a count-based sliding window of 20 calls, a minimum of 10 calls before the failure rate is evaluated, a 50% failure-rate threshold, a 10-second open-state wait, and 5 trial calls permitted in the half-open state.

Retry is applied only to read methods (`postgres-read`, `redis-read`), each allowing 3 attempts with a 100ms base wait and exponential backoff (multiplier 2). Write methods (`save`, `saveFirstOwner`, `revokeRefreshToken`, etc.) are annotated with `@CircuitBreaker` only — never `@Retry` — because retrying a write whose response was lost in transit risks double-applying it.

**Aspect ordering is explicit and matters.** `application.yml` sets `circuitBreakerAspectOrder: 1` and `retryAspectOrder: 2`, which makes the circuit breaker the *outer* aspect and retry the *inner* one — the reverse of Resilience4j's documented default. This way a call that fails once and is retried successfully is recorded as a single success against the circuit breaker's sliding window, not as one failure followed by one success; getting this backwards would silently inflate the measured failure rate under normal, expected transient blips and could trip the breaker under ordinary load.

`PostgresAuditLog` (see [ADR-0009](0009-domain-audit-log.md)) reuses the same `postgres` circuit breaker instance as `PostgresUserRepository` rather than defining a separate one, since both share the same underlying datasource and the same failure mode.

## Consequences

**Positive:**
- A degraded PostgreSQL or Redis instance fails fast once the breaker opens, instead of every request queuing behind a slow dependency and exhausting virtual threads.
- Read-side resilience (retry) and write-side safety (no blind retry) are encoded as a structural rule (which annotations are present), not left to each adapter author's judgment per call site.
- The aspect-ordering fix is documented at the point of configuration, preventing a future contributor from "simplifying" it back to the library default and silently breaking the failure-rate accounting.

**Negative:**
- Two independent breaker instances (`postgres`, `redis`) mean an operator must understand which logical dependency maps to which instance name when reading metrics or logs — there is no single global breaker.
- Tuning values (window size, threshold, wait duration) are defaults chosen for a boilerplate; a production deployment under real traffic will likely need to retune them against observed latency and error-rate baselines.

## Alternatives Considered

- **Connection-pool timeouts only (no circuit breaker)** — rejected: a timeout still pays the full latency cost on every call to a degraded dependency, whereas an open breaker fails immediately.
- **Retry on writes with idempotency keys** — rejected as out of scope for a boilerplate; it would require an idempotency-key contract across every write path, a larger design decision than this ADR's scope.
- **A single shared circuit breaker instance for all external dependencies** — rejected: PostgreSQL and Redis fail independently and at different rates; coupling them into one breaker would open the breaker for both on a failure in either.
