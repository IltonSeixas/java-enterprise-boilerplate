# ADR 0012 — Messaging, Queues, and Asynchronous Processing

## Status

Informational — this ADR does not record a decision made for this boilerplate. It documents the trade-offs and requirements for adding messaging or asynchronous processing.

## Context

The boilerplate is synchronous by design: every use case completes within the HTTP/gRPC request thread. This is intentional for an IAM boilerplate where the domain is small and all operations are fast and predictable.

However, real systems built on this boilerplate will likely need asynchronous processing for:
- Sending transactional emails (registration confirmation, password reset)
- Webhook delivery
- Audit log archiving
- Long-running background jobs (report generation, data exports)
- Event-driven integration between bounded contexts

This ADR documents what to build when you need it, and the trade-offs of each technology choice.

---

## The Complexity Warning

**Adding a message broker is a significant architectural commitment.** Before adding one, answer:

1. Does the operation truly need to be async? (Could a sync call with a timeout solve it?)
2. What happens if the message is lost? (Is at-least-once delivery required?)
3. Who is the consumer? (Same service? Another service? Third-party webhook?)
4. What is the expected message rate? (Hundreds/day vs. millions/hour changes the answer.)

If you cannot answer these questions, do not add a broker yet.

---

## Broker Options and Trade-offs

### RabbitMQ

**Best for:** task queues, work queues, fan-out, flexible routing with exchanges and bindings.

| Dimension | Assessment |
|---|---|
| Delivery guarantee | At-least-once (with acks); exactly-once requires idempotent consumers |
| Message ordering | Per-queue FIFO; no global ordering |
| Replay / rewind | No native replay (use a dedicated stream plugin for that) |
| Throughput | Moderate (tens of thousands/sec per queue) |
| Operational complexity | Moderate — clustering requires quorum queues for HA |
| DLQ support | Native (`x-dead-letter-exchange`) |
| Spring integration | `spring-rabbit` (Spring AMQP) |

**When to choose:** transactional emails, webhook delivery, intra-service task offloading. The routing flexibility (topic exchanges, direct exchanges) makes it well-suited for complex fan-out patterns.

**Warnings:**
- Message loss is possible if a queue is not durable and the broker restarts. Always declare queues as `durable=true` and publish with `persistent=true`.
- Quorum queues are strongly preferred over classic mirrored queues since RabbitMQ 3.8.
- Without DLQ configuration, failed messages are silently dropped after max retry.

---

### Apache Kafka

**Best for:** event streaming, event sourcing, log compaction, high-throughput pipelines, replay.

| Dimension | Assessment |
|---|---|
| Delivery guarantee | At-least-once by default; exactly-once with transactions and idempotent producer |
| Message ordering | Per-partition (key-based routing) |
| Replay / rewind | Native — consumers can reset offsets to any point |
| Throughput | Very high (millions of messages/sec per cluster) |
| Operational complexity | High — Kafka needs ZooKeeper (or KRaft), careful partition sizing, monitoring |
| DLQ support | Not native; must build with a dedicated error topic |
| Spring integration | `spring-kafka` |

**When to choose:** event sourcing, audit log streaming, CDC (Change Data Capture), inter-service event bus at scale, when replay of historical events is required.

**Warnings:**
- Kafka is operationally heavy for small workloads. A 3-broker cluster is the minimum for production HA.
- Consumer group rebalancing pauses consumption. Tune `session.timeout.ms` and `heartbeat.interval.ms` appropriately.
- Ordering guarantees apply only within a partition. If you shard by user_id, events for the same user are ordered, but events across users are not.
- Adding Kafka for a use case that RabbitMQ can handle is over-engineering.

---

### Amazon SQS

**Best for:** cloud-native AWS workloads, simple task queues, serverless consumers (Lambda).

| Dimension | Assessment |
|---|---|
| Delivery guarantee | At-least-once (standard queue); FIFO queue adds deduplication |
| Message ordering | Standard: unordered. FIFO: ordered per message group |
| Replay / rewind | No native replay |
| Throughput | Standard: nearly unlimited. FIFO: 300 TPS per API action (3000 with batching) |
| Operational complexity | Low — fully managed |
| DLQ support | Native (set `RedrivePolicy` on the queue) |
| Spring integration | `spring-cloud-aws-messaging` or AWS SDK v2 directly |

**When to choose:** AWS-native stacks, Lambda consumers, when operational overhead of self-managed brokers is unacceptable.

**Warnings:**
- SQS visibility timeout must exceed the maximum consumer processing time. If a consumer times out without deleting the message, it becomes visible again (at-least-once).
- Standard queues can deliver messages out of order and more than once. Design consumers to be idempotent.
- FIFO queues have a 10x lower throughput ceiling than standard queues.

---

### Cloudflare Queues

**Best for:** edge-native Cloudflare Workers workloads.

| Dimension | Assessment |
|---|---|
| Delivery guarantee | At-least-once |
| Ordering | Not guaranteed |
| Replay | No |
| Throughput | 5,000 messages/batch/consumer (subject to Workers limits) |
| Operational complexity | Very low — fully managed |
| DLQ support | Native dead-letter queue per queue |
| Spring integration | None — HTTP-based API only; incompatible with a JVM server |

**When to choose:** If consumers are Cloudflare Workers. Not applicable to a Spring Boot application unless the producer side is a Worker calling a Spring endpoint.

---

## Requirements for Production-Grade Async Processing

If you add messaging to this boilerplate, implement **all** of the following:

### 1. Durability (Outbox Pattern)

Do not publish messages from within a business transaction. A crash between the DB commit and the broker publish loses the event. Use the transactional outbox pattern:

1. Write the event to an `outbox` table **in the same transaction** as the business change.
2. A background relay (polling or CDC) reads the outbox and publishes to the broker.
3. Mark events as published (or delete them) after broker acknowledgment.

### 2. Idempotency

Consumers must be idempotent. At-least-once delivery means the same message can arrive multiple times (broker retry, consumer crash before ack, network partition). Use:
- A `processed_message_ids` table or Redis set to record seen message IDs.
- Domain operations that are naturally idempotent (e.g., `SET` instead of `INCREMENT`).

### 3. Dead Letter Queue (DLQ)

Every consumer must have a DLQ. Configure the maximum number of delivery attempts (typically 3–5) before a message is routed to the DLQ. Alert on DLQ depth.

### 4. Retry with Exponential Backoff

Between delivery attempts, apply exponential backoff with jitter:
```
delay = min(base_delay * 2^attempt + random_jitter, max_delay)
```
For RabbitMQ, use `x-delay` (requires the Delayed Message Exchange plugin) or requeue with a delay queue. For Kafka, use a retry topic per delay tier (`topic.retry.5s`, `topic.retry.30s`, etc.).

### 5. Heartbeat and Consumer Timeout

Configure broker-side heartbeats and consumer-side health checks. For Kafka: `heartbeat.interval.ms` (default 3s) must be less than `session.timeout.ms` (default 45s). Set `max.poll.interval.ms` to be longer than the maximum processing time per batch.

### 6. Observability

Every message must carry:
- A correlation ID (propagate the HTTP `X-Request-ID` or OpenTelemetry trace ID).
- A timestamp (`published_at`) for latency measurement.
- An event type for routing and monitoring.

Expose metrics: queue depth, consumer lag, DLQ depth, processing duration histogram.

### 7. Security

- Encrypt messages at rest (broker-level) and in transit (TLS).
- Use least-privilege credentials per consumer (never share a producer key with a consumer).
- Validate message schemas at the consumer boundary (do not trust the broker to enforce schema).
- Never include PII in message bodies unless the broker storage is compliant with your data residency requirements.

---

## How to Add a Broker to This Boilerplate

1. Define a `MessagePublisherPort` interface in the `application/port/out` package.
2. Add an `outbox` table migration to Flyway.
3. Implement the outbox relay as an `@Scheduled` task or CDC adapter.
4. Implement `MessagePublisherPort` for the chosen broker (annotate `@Profile`).
5. Implement a consumer with DLQ, retry, and idempotency as described above.
6. Add Testcontainers integration tests for the publisher and consumer.

**Do not implement messaging without a concrete domain use case.** Skeleton implementations without consumers create dead code and documentation that misleads future developers.
