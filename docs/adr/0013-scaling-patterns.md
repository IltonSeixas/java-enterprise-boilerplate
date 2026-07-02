# ADR 0013 — Scaling Patterns: Read Replicas, Cache, Sharding, Batching

## Status

Informational — this ADR documents the scaling patterns available to teams building on this boilerplate, along with trade-offs and the complexity cost of each.

## Context

The boilerplate is optimized for clarity and correctness at moderate scale. It uses a single PostgreSQL primary with HikariCP connection pooling and Redis for token/rate-limit storage.

As a system grows, teams will evaluate:
1. **Read replicas** — offloading reads from the primary
2. **Application-level cache** — reducing database round-trips
3. **Sharding** — distributing writes horizontally
4. **Batch processing** — handling bulk operations efficiently

Each pattern solves a specific bottleneck. Applying them prematurely adds operational complexity without benefit. This ADR helps teams identify when each pattern becomes necessary and what it costs.

---

## Complexity Warning

**Measure before you optimize.** Add a scaling pattern only after:
1. You have measured the bottleneck (Prometheus metrics, slow query logs, APM traces).
2. Simpler alternatives (index tuning, query optimization, connection pool sizing) have been exhausted.
3. The team accepts the operational overhead of the new pattern.

The most common mistake is adding infrastructure to solve a problem that does not exist yet.

---

## 1. Read Replicas

### What it solves

PostgreSQL primary CPU/IO is saturated by read queries. The primary cannot keep up with read + write load simultaneously.

### How it works

Add a streaming replica. Route `@Transactional(readOnly = true)` queries to the replica URL; writes go to the primary. In Spring, implement `AbstractRoutingDataSource`:

```java
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? "replica" : "primary";
    }
}
```

Configure two datasources in the postgres profile:
```yaml
app.datasource.primary.url: ${DB_PRIMARY_URL}
app.datasource.replica.url: ${DB_REPLICA_URL}
```

### Trade-offs

| Pro | Con |
|---|---|
| Doubles read capacity without changing application code | Replication lag — replica may be 10ms–seconds behind primary |
| Zero downtime to add | Reads after a write may see stale data (read-your-writes violation) |
| Transparent to use cases (they already use `readOnly=true`) | Two connection pools to size and monitor |
| No schema changes | Failover requires manual promotion or a proxy (pgBouncer, RDS Multi-AZ) |

### When to add

- Primary CPU > 70% sustained under read load.
- Slow query log shows read queries competing with writes.
- Write throughput is acceptable; only reads are the bottleneck.

### When NOT to add

- Read traffic is low (< 1000 req/min). Index optimization will solve it.
- Application has many "read-your-writes" patterns (login → immediate profile read). The replica lag will cause visible inconsistencies.
- Simpler alternative: PgBouncer in front of a single primary with connection multiplexing.

---

## 2. Application-Level Cache (Redis)

### What it solves

Repeated reads of the same data hit the database on every request. Hot paths (e.g., loading a user on every authenticated request) generate unnecessary DB load.

### Current state

Redis is already in the stack for token storage. Adding Spring Cache requires only configuration, no new infrastructure.

### How to add

Enable Spring's cache abstraction:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(User.class)));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
```

Then annotate the infrastructure adapter (never the domain):

```java
// PostgresUserRepository
@Cacheable(value = "users", key = "#id.value()")
public Optional<User> findById(UserId id) { ... }

@CacheEvict(value = "users", key = "#user.id.value()")
public void save(User user) { ... }
```

### Trade-offs

| Pro | Con |
|---|---|
| Dramatic reduction in DB reads for hot data | Stale cache — a write in one instance may not evict cache in another |
| Redis is already in the stack | Cache invalidation is one of the hardest problems in CS |
| Transparent to use cases | Adds serialization complexity (User must be JSON-serializable) |
| TTL-based expiry is simple to reason about | Cache stampede under high load when TTL expires simultaneously |

### Invalidation strategies

- **TTL-only**: simplest; accept that reads may see data up to TTL seconds stale. Appropriate for user profiles where 5-minute staleness is acceptable.
- **Write-through**: evict/update cache on every write. Requires `@CacheEvict` on all mutating methods. Consistent but adds write latency.
- **Event-driven**: publish a cache invalidation event on write; all instances evict on receipt. Most consistent but requires a message bus.

### When to add

- Profile page or user object is loaded on every request and shows in slow query traces.
- `JwtAuthenticationFilter` currently hits the database on every request to validate the user is still active. Caching the user with a 5-minute TTL reduces this to zero DB reads for active sessions.

### When NOT to add

- Data mutates frequently (e.g., user balance, inventory count). The stale window creates correctness bugs.
- The team cannot reason clearly about invalidation. Wrong invalidation is worse than no cache.
- Premature: if DB latency is < 2ms with indexes, cache adds complexity for no measurable benefit.

---

## 3. Sharding

### What it solves

A single PostgreSQL primary cannot handle the write throughput required by the application, even with read replicas offloading reads.

### What it costs

Sharding is the most expensive architectural decision on this list:
- **No cross-shard transactions**: operations that span multiple users in different shards require distributed sagas or two-phase commit.
- **Cross-shard queries are expensive**: aggregations and joins across shards require application-level scatter-gather or a query federation layer.
- **Schema changes are multiplied**: a migration must run on every shard.
- **Rebalancing is painful**: moving data between shards during growth requires careful coordination.
- **Operational complexity multiplies**: N shards = N databases to monitor, back up, and failover.

### Sharding strategies

| Strategy | Description | Best for |
|---|---|---|
| Key-based (hash) | `shard_id = hash(user_id) % N` | Even distribution, predictable routing |
| Range-based | shard by `id` range or `created_at` range | Time-series, archival |
| Directory-based | Lookup table maps key → shard | Maximum flexibility, but lookup table is a bottleneck |

### Alternatives to evaluate first

1. **Vertical scaling**: a modern PostgreSQL primary on a 64-core, 512 GB RAM instance handles tens of thousands of writes/sec.
2. **Partitioning**: range or hash partitioning within a single Postgres instance (like `audit_log` in this boilerplate) handles most "too much data" problems without distributed complexity.
3. **CQRS + Event Sourcing**: separate write and read models entirely.
4. **Citus** (Postgres extension): transparent sharding within the Postgres wire protocol. Avoids many of the application-layer sharding problems.

### When to consider

Write throughput > 50,000 TPS on the primary after all other optimizations are exhausted, and vertical scaling is not an option.

### Recommendation for this boilerplate

Do not add sharding. The current domain (IAM) does not approach the scale where sharding is warranted. If a team building on this boilerplate reaches that scale, they will have the resources and expertise to design a sharding strategy appropriate to their specific domain and access patterns.

---

## 4. Batching

### What it solves

Processing large volumes of records one at a time is inefficient: each record incurs a round-trip to the database, queue, or external service.

### Types of batching

#### Database batch inserts

Hibernate supports JDBC batching. Enable in the postgres profile:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

This batches up to 50 INSERT/UPDATE statements into a single JDBC call. Effective when saving collections of entities.

#### Bulk operations via `saveAll()`

`JpaUserRepository` extends `JpaRepository` which provides `saveAll(Iterable<User>)`. Combined with JDBC batching above, this is efficient for bulk imports.

#### Spring Batch

For scheduled bulk jobs (nightly reports, data exports, ETL), Spring Batch provides:
- Chunk-oriented processing (read N, process N, write N)
- Restart/retry on failure (job state persisted in a `BATCH_*` schema)
- Partitioned (parallel) steps
- Listeners and skip policies

**When to add Spring Batch:** recurring bulk operations on > 10,000 records where failure recovery and restartability matter.

**When NOT to add:** ad-hoc queries, one-time data migrations (use Flyway), or operations that process < 1,000 records.

### Trade-offs of batching

| Pro | Con |
|---|---|
| Dramatically reduces DB round-trips | Harder to debug individual record failures |
| Reduces connection pool contention | Transactions hold locks longer (affects concurrent reads) |
| Spring Batch provides restartability | Spring Batch adds schema (BATCH_JOB_INSTANCE, etc.) and dependencies |
| Efficient for bulk imports/exports | May introduce latency for the first record (waits for batch to fill) |

---

## Summary: When to Add Each Pattern

| Pattern | Add when | Approximate threshold |
|---|---|---|
| Read replica | Primary CPU bound on reads | > 70% CPU sustained |
| Application cache | Hot read paths in traces | > 100 req/sec to same key |
| Sharding | Write throughput exceeds single primary | > 50,000 TPS writes |
| JDBC batching | Bulk inserts/updates are slow | > 1,000 records per operation |
| Spring Batch | Recurring bulk jobs with recovery | > 10,000 records, scheduled |

**Always measure first. Never guess.**
