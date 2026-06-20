# ADR-0009: Immutable Domain Audit Log for Sensitive Events

**Date:** 2026-06-20
**Status:** Accepted

---

## Context

A financial-institution-grade system needs a tamper-evident record of who did what, to whom, and when — independent of the application's regular log stream, which is typically rotated, sampled, or shipped to a system optimized for operational debugging rather than forensic review. The events that matter for this purpose are a small, fixed set tied to identity and access: registration, login (success and failure), password change, role change, logout, and token refresh.

The core must learn about these events without depending on a concrete logging or storage technology, consistent with [ADR-0001](0001-clean-architecture.md)'s Clean Architecture boundary and [ADR-0007](0007-zero-spring-in-application-layer.md)'s zero-Spring-in-`application/` rule.

## Decision

**Domain.** `domain/audit/AuditEvent` is an immutable record (`id`, `occurredAt`, `type`, `actorUserId`, `targetUserId`, `detail`) with a constructor that rejects a null type or a blank actor. `AuditEventType` enumerates the seven sensitive events. `actorUserId` is the identity that triggered the action; `targetUserId` is who it affected (equal to the actor for self-initiated events such as login, defaulted via an overload of `AuditEvent.of(...)`).

**Application.** `application/port/out/AuditPort` is a single-method port (`void record(AuditEvent event)`). Each sensitive use case (`RegisterUserUseCase`, `LoginUserUseCase`, `ChangePasswordUseCase`, `ChangeUserRoleUseCase`, `LogoutUseCase`, `RefreshTokenUseCase`) takes an `AuditPort` constructor parameter and calls `record(...)` at the point an event actually occurred — including failure paths for login, since repeated failed logins against an account or a non-existent email are themselves a security-relevant signal.

**Infrastructure.** Two adapters mirror the project's existing in-memory/Postgres adapter pairing:
- `InMemoryAuditLog` (`@Profile("inmemory")`) appends to a `CopyOnWriteArrayList` and logs at INFO — the zero-config default, with no persistence guarantee across restarts.
- `PostgresAuditLog` (`@Profile("postgres")`) inserts into a new `audit_log` table (Flyway migration `V2__audit_log.sql`), wrapped in the same Resilience4j `postgres` circuit breaker used by `PostgresUserRepository` — see [ADR-0008](0008-resilience4j-fault-tolerance.md).

**Auditing never fails the use case it observes.** `PostgresAuditLog.record(...)` catches any exception from the underlying insert and logs a warning instead of propagating — a Postgres outage degrades audit coverage, it does not block login, registration, or any other sensitive operation. This is a deliberate availability-over-completeness trade-off: an audit gap during an outage is recoverable from the regular application log; a login outage is not acceptable collateral damage from an audit-store outage.

**Immutability is enforced by interface shape, not database grants.** `AuditPort` exposes only `record(...)` — no update or delete method exists anywhere a use case could call it. This was chosen over a `REVOKE UPDATE, DELETE ... FROM PUBLIC` SQL grant, which was tried first and rejected: the application connects as the table's owning role, and table owners in PostgreSQL always retain full privileges over their own tables regardless of grants to `PUBLIC` — the revoke would have been a no-op that looked like a real protection.

## Consequences

**Positive:**
- The set of auditable actions is explicit and exhaustive — adding a new sensitive use case is a compile-time reminder (it needs an `AuditPort` constructor parameter) rather than an easily-forgotten logging call buried in a method body.
- Audit failures cannot take down authentication; the system degrades gracefully under audit-store outages.
- The in-memory/Postgres adapter pairing keeps the zero-config "clone and run" experience intact — no external dependency is required to exercise audited flows in tests or local development.

**Negative:**
- `audit_log` immutability is a code-level guarantee (interface shape), not a database-level one — a future migration or a direct SQL session against the database could still mutate rows. True database-enforced immutability would require a separate, lower-privileged application role without `UPDATE`/`DELETE` grants, which is out of scope for a boilerplate's default setup.
- `InMemoryAuditLog`'s history is lost on restart, by design (it follows the same volatility contract as the rest of the in-memory profile).

## Alternatives Considered

- **`REVOKE UPDATE, DELETE ... FROM PUBLIC` on `audit_log`** — tried first; rejected as ineffective once `PUBLIC` grants were understood not to bind the table's owning role.
- **Domain events + an event bus** (publish `AuditEvent` as a domain event, subscribe a listener that persists it) — rejected as over-engineering for a fixed, small set of six call sites with no other subscriber; a direct port call is simpler and equally testable.
- **Audit as an AOP/Resilience4j-style annotation on use case methods** — rejected because it would require Spring (or another AOP framework) to reach into `application/`, violating ADR-0007 outright.
