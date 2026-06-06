# Configuration

All configuration is managed via Spring Boot's externalized configuration mechanism. Properties are read from `application.yml` and overridden by environment variables using Spring's relaxed binding (e.g., `JWT_SECRET` overrides `jwt.secret`).

The application fails fast with a clear error message if any required property is missing — enforced via `@ConfigurationProperties` with `@Validated`.

A `application.example.yml` file in `src/main/resources/` lists every property. Copy it for local development:

```bash
cp src/main/resources/application.example.yml src/main/resources/application-local.yml
```

Activate with: `--spring.profiles.active=local`

---

## Reference

### Server

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `3000` | HTTP listen port |
| `GRPC_PORT` | No | `50051` | gRPC listen port |

### Persistence

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `ADAPTER` | No | `memory` | Persistence adapter: `memory` or `postgres` |
| `SPRING_DATASOURCE_URL` | If `postgres` | — | JDBC PostgreSQL URL |
| `SPRING_DATASOURCE_USERNAME` | If `postgres` | — | Database user |
| `SPRING_DATASOURCE_PASSWORD` | If `postgres` | — | Database password |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | No | `10` | HikariCP max pool size |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | No | `2` | HikariCP min idle connections |

### Cache

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `SPRING_DATA_REDIS_URL` | If `postgres` | — | Redis connection URL |

### Authentication

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | Yes | — | RSA private key path or inline PEM — never a shared secret |
| `JWT_PUBLIC_KEY` | Yes | — | RSA public key path or inline PEM |
| `JWT_ACCESS_TTL` | No | `900` | Access token TTL in seconds (15 min) |
| `JWT_REFRESH_TTL` | No | `604800` | Refresh token TTL in seconds (7 days) |

### Security

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `ALLOWED_ORIGINS` | No | `http://localhost:*` | Comma-separated CORS allowed origins |
| `RATE_LIMIT_RPS` | No | `100` | Max requests per second per IP |

### Observability

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `LOGGING_LEVEL_ROOT` | No | `INFO` | Root log level |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | No | `1.0` | Trace sampling rate (lower in production) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | `http://localhost:4318/v1/traces` | OTLP HTTP endpoint |
| `OTEL_SERVICE_NAME` | No | `java-enterprise-boilerplate` | Service name in traces |

### Spring Boot Actuator

| Property | Required | Default | Description |
|---|---|---|---|
| `management.endpoints.web.exposure.include` | No | `health,prometheus` | Exposed actuator endpoints |
| `management.endpoint.health.show-details` | No | `never` | Never expose health details publicly |

---

## Production Checklist

Before deploying to production:

- [ ] `JWT_SECRET` and `JWT_PUBLIC_KEY` are generated RSA key pairs — never shared across environments
- [ ] `SPRING_DATASOURCE_URL` uses a TLS JDBC URL (`?ssl=true&sslmode=require`)
- [ ] `SPRING_DATASOURCE_PASSWORD` is injected via a secrets manager
- [ ] `SPRING_DATA_REDIS_URL` uses a password-protected Redis instance
- [ ] `ALLOWED_ORIGINS` lists only your actual frontend domains
- [ ] `LOGGING_LEVEL_ROOT` is `INFO` or `WARN` — never `DEBUG` or `TRACE`
- [ ] `spring.jpa.show-sql` is `false` (default) — never `true` in production
- [ ] `management.endpoint.health.show-details` is `never` in production
- [ ] All secrets are injected via a secrets manager — never in `application.yml` committed to source control
