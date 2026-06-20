# Observability

## Overview

Observability is built into the boilerplate from the start using **Micrometer** (metrics) and **Micrometer Tracing** with the OpenTelemetry bridge (traces). You can export to any compatible backend (Jaeger, Grafana Tempo, Honeycomb, Datadog, AWS X-Ray) by changing configuration properties.

The three pillars — **traces**, **metrics**, and **logs** — are correlated by trace ID via Logback's MDC integration, so you can move seamlessly from a high-level metric spike to the exact trace, then to the log lines of the failing request.

---

## Traces

Every HTTP request is automatically instrumented by Spring Boot's auto-configuration for Micrometer Tracing. Use cases emit custom observations via the `Observation` API.

### Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 1.0   # 100% in dev; tune for production
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
```

### Manual observations in use cases

```java
public class RegisterUserUseCase {

    private final ObservationRegistry registry;

    public RegisterUserUseCase(ObservationRegistry registry) {
        this.registry = registry;
    }

    public void execute(RegisterUserCommand command) {
        Observation.createNotStarted("register_user", registry)
            .lowCardinalityKeyValue("adapter", "postgres")
            .observe(() -> {
                // use case logic
            });
    }
}
```

The `Observation` API is the Micrometer abstraction — it generates both a trace span and a timer metric from the same instrumentation point. The use case itself stays a plain class; the `ObservationRegistry` bean is supplied as a constructor argument from the `@Bean` factory method in `infrastructure/config/UseCaseConfig` (see [ADR-0007](adr/0007-zero-spring-in-application-layer.md)).

---

## Metrics

Prometheus-format metrics are exposed via Spring Boot Actuator at `/actuator/prometheus`. Micrometer auto-configures JVM, Tomcat, HikariCP, and HTTP request metrics.

### Available metrics (selection)

| Metric | Type | Description |
|---|---|---|
| `http.server.requests` | Timer | HTTP request latency by method, URI, status |
| `jvm.memory.used` | Gauge | JVM heap and non-heap usage |
| `jvm.threads.live` | Gauge | Live thread count (includes Virtual Threads) |
| `hikaricp.connections.active` | Gauge | Active database connections |
| `use_case.register_user` | Timer | Use case execution latency |

### Scrape config (Prometheus)

```yaml
scrape_configs:
  - job_name: java-api
    static_configs:
      - targets: ['localhost:3000']
    metrics_path: /actuator/prometheus
```

---

## Logs

Structured JSON logs via **Logback** + `logstash-logback-encoder`. Micrometer Tracing automatically injects `traceId` and `spanId` into the MDC, so every log line within a request is correlated.

### Log format (production)

```json
{
  "@timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger_name": "com.enterprise.boilerplate.application.usecase.RegisterUserUseCase",
  "message": "user registered",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "userId": "01HN..."
}
```

### Log levels

| Level | Use |
|---|---|
| `ERROR` | Unrecoverable failures — always paged |
| `WARN` | Recoverable unexpected states |
| `INFO` | Business events (user registered, login succeeded) |
| `DEBUG` | Development only — never in production |
| `TRACE` | Never in production |

```yaml
logging:
  level:
    root: INFO
    com.enterprise.boilerplate: INFO
```

Production must never have `spring.jpa.show-sql=true` (logs query parameters).

---

## Configuration

| Property / Variable | Default | Description |
|---|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP HTTP endpoint |
| `OTEL_SERVICE_NAME` | `java-enterprise-boilerplate` | Service name in traces |
| `management.tracing.sampling.probability` | `1.0` | Trace sampling rate (lower in production) |
| `logging.level.root` | `INFO` | Root log level |

---

## Health Checks

Spring Boot Actuator exposes health indicators at `/actuator/health`. In addition to the auto-configured `db`, `redis`, `diskSpace` and `ssl` indicators, three custom indicators cover gaps the defaults don't:

| Indicator | Reports `DOWN` when |
|---|---|
| `jwtKeys` | The Ed25519 PEM key pair at `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` is missing, unreadable, or fails to parse |
| `grpcServer` | The embedded gRPC server (`GrpcServerLifecycle`) is not running; this also writes `SERVING`/`NOT_SERVING` to the standard `grpc.health.v1.Health` service (via `HealthStatusManager`), so `grpc_health_probe` and `/actuator/health` always agree |
| `auditLog` | The Postgres audit log table is unreachable (active only under the `postgres` profile) |

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "jwtKeys": { "status": "UP" },
    "grpcServer": { "status": "UP" }
  }
}
```

Liveness (`/actuator/health/liveness`) and readiness (`/actuator/health/readiness`) probes are configured separately for Kubernetes deployments.

Per-component detail is restricted to authenticated requests (`management.endpoint.health.show-details: when_authorized`); unauthenticated callers only see the aggregate `status`.

## Build Information

`/actuator/info` exposes build metadata (artifact, group, name, version, build time) sourced from `target/classes/META-INF/build-info.properties`, generated by the `spring-boot-maven-plugin`'s `build-info` goal during `mvn package`. This file is not produced by `spring-boot:run` against `target/classes` directly — only by a full Maven build.

---

## Local Development

Start a local Jaeger all-in-one instance to visualize traces:

```bash
docker compose up jaeger
```

Open `http://localhost:16686` to browse traces.

The `docker-compose.yml` in the repository root includes Jaeger, Prometheus, and Grafana pre-configured.
