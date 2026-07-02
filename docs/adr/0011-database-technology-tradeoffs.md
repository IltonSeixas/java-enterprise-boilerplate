# ADR 0011 — Database Technology Trade-offs

## Status

Informational — this ADR does not record a decision made for this boilerplate. It documents the trade-offs to guide developers choosing (or replacing) the persistence layer.

## Context

The boilerplate ships with a PostgreSQL adapter (`@Profile("postgres")`) and an in-memory adapter (`@Profile("inmemory")`) as the zero-config default. The domain is fully decoupled from the persistence technology: `UserRepository` is a plain Java interface and use cases depend only on it.

Developers targeting different workloads may want to swap or add adapters for:

- **Apache Cassandra** — wide-column, distributed
- **MongoDB** — document store
- **Redis** — in-memory key-value (already present as a token/rate-limit store)

## Options and Trade-offs

### PostgreSQL (current default)

**Best for:** relational data with complex queries, strong consistency, ACID transactions, small-to-medium scale.

| Dimension | Assessment |
|---|---|
| Consistency | Strong (ACID, serializable isolation available) |
| Query model | Full SQL, JOINs, aggregations, window functions |
| Schema evolution | Flyway migrations — controlled, auditable |
| Horizontal write scale | Limited; needs logical replication or Citus for sharding |
| Read scale | Read replicas via streaming replication |
| Operational complexity | Low for a single primary; moderate with replicas |

**When to stay:** user counts below ~10M rows, complex access patterns, need for transactional guarantees across entities.

**When to migrate away:** write throughput exceeds what a single primary can handle and sharding is not feasible; or data is naturally document-shaped.

---

### Apache Cassandra

**Best for:** massive write throughput, geographic distribution, time-series data, eventual consistency is acceptable.

| Dimension | Assessment |
|---|---|
| Consistency | Tunable (ONE → QUORUM → ALL); eventual by default |
| Query model | CQL — no JOINs; queries must match partition key |
| Schema evolution | ALTER TABLE is online but limited |
| Horizontal write scale | Excellent — linear scaling via partitioning |
| Read scale | Good within partition; cross-partition is expensive |
| Operational complexity | High — compaction, repair, tuning, anti-patterns |

**When to consider:** audit log volumes in the billions; IoT telemetry; write-heavy workloads where you can denormalize data per query.

**Warnings:**
- No secondary indexes that perform well at scale; queries must be designed around partition keys.
- No multi-row transactions. Lightweight transactions (LWT) exist but are expensive.
- Schema changes (`DROP COLUMN`, `RENAME`) can be irreversible or require data migration.
- Adding Cassandra alongside Postgres doubles operational burden. Evaluate whether Postgres with partitioning and read replicas solves the problem first.

**How to add:** implement `UserRepository` with Spring Data Cassandra or the Datastax driver, annotate `@Profile("cassandra")`, add a `cassandra` profile to `application.yml`.

---

### MongoDB

**Best for:** flexible, semi-structured documents; rapid schema iteration; hierarchical/nested data.

| Dimension | Assessment |
|---|---|
| Consistency | Strong within a single document; eventual across shards |
| Query model | Rich document queries; aggregation pipeline; no JOINs |
| Schema evolution | Schema-less — flexible but requires application-level validation |
| Horizontal write scale | Good via sharding |
| Read scale | Replica sets |
| Operational complexity | Moderate |

**When to consider:** product catalog, CMS, user profiles with variable attributes, deeply nested data that maps poorly to relational tables.

**Warnings:**
- Multi-document ACID transactions exist (since 4.0) but add overhead. Design to avoid them.
- "Schema-less" is a double-edged sword: without strict validation at the application boundary (enforce with Zod/Bean Validation), data quality degrades over time.
- Aggregation pipelines can be powerful but are difficult to optimize and maintain.
- For this boilerplate's current domain (user IAM), a relational model is a better fit than documents.

**How to add:** implement `UserRepository` with Spring Data MongoDB, annotate `@Profile("mongo")`.

---

### Redis (supplemental — already present)

Redis is **not** a replacement for the primary database. It is used in this boilerplate as:
1. Refresh token store (current use)
2. Rate limit counter (current use)
3. Application-level cache (optional extension — see ADR 0013)

Using Redis as a primary store is an anti-pattern for this domain: keys have no schema, and persistence guarantees depend on RDB/AOF configuration.

---

## Decision for This Boilerplate

PostgreSQL remains the production adapter. The architecture supports adding any adapter without touching domain or application layers. Add a new adapter by:

1. Implementing `UserRepository` and `AuditPort` with the target technology.
2. Annotating the implementations with `@Profile("<technology>")`.
3. Adding the profile configuration to `application.yml`.
4. Adding a new integration test using Testcontainers.

**Do not add a new adapter without a use case that justifies it.** Unused adapters are maintenance debt.
