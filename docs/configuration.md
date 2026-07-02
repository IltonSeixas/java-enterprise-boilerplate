# Configuration

All configuration is managed via Spring Boot's externalized configuration mechanism. Properties are read from `application.yml` and overridden by environment variables using Spring's relaxed binding (e.g., `JWT_PRIVATE_KEY_PATH` overrides `jwt.private-key-path`).

`.env.example` in the project root lists every variable read by `application.yml`. Copy it for local development:

```bash
cp .env.example .env
```

Activate the PostgreSQL profile with: `SPRING_PROFILES_ACTIVE=postgres`

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
| `SPRING_PROFILES_ACTIVE` | No | `inmemory` | Active Spring profile — selects the persistence adapter: `inmemory` or `postgres` |
| `SPRING_DATASOURCE_URL` | If `postgres` | `jdbc:postgresql://localhost:5432/boilerplate` | JDBC PostgreSQL URL |
| `SPRING_DATASOURCE_USERNAME` | If `postgres` | `boilerplate` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | If `postgres` | — | Database password |
| `DB_POOL_SIZE` | No | `10` | HikariCP maximum pool size |
| `DB_POOL_MIN_IDLE` | No | `2` | HikariCP minimum idle connections |
| `DB_POOL_CONNECT_TIMEOUT_MS` | No | `30000` | HikariCP connection acquisition timeout (ms) |
| `DB_POOL_IDLE_TIMEOUT_MS` | No | `600000` | HikariCP idle connection eviction timeout (ms) |
| `DB_POOL_MAX_LIFETIME_MS` | No | `1800000` | HikariCP maximum connection lifetime (ms) |

### Cache

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `SPRING_DATA_REDIS_URL` | No | `redis://localhost:6379` | Redis connection URL (refresh token storage) |
| `REDIS_CONNECT_TIMEOUT_MS` | No | `2000` | Lettuce connection timeout (ms) |
| `REDIS_COMMAND_TIMEOUT_MS` | No | `2000` | Lettuce command execution timeout (ms) |

### Authentication

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `JWT_PRIVATE_KEY_PATH` | Yes (production) | `jwt_private.pem` | Path to the Ed25519 PEM private key used to sign access tokens |
| `JWT_PUBLIC_KEY_PATH` | Yes (production) | `jwt_public.pem` | Path to the Ed25519 PEM public key used to verify access tokens |
| `JWT_ACCESS_EXPIRY_MINUTES` | No | `15` | Access token TTL in minutes |
| `JWT_REFRESH_EXPIRY_DAYS` | No | `7` | Refresh token TTL in days |

### Security

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5173` | Comma-separated CORS allowed origins |
| `RATE_LIMIT_TRUST_FORWARDED_HEADERS` | No | `false` | Whether `AuthRateLimitFilter` trusts `X-Forwarded-For` to resolve the client IP — enable only behind a trusted reverse proxy that sets this header itself |

### Observability

| Property / Env Var | Required | Default | Description |
|---|---|---|---|
| `LOGGING_LEVEL_ROOT` | No | `INFO` | Root log level |
| `LOGGING_LEVEL_APP` | No | `INFO` | Log level for `com.enterprise.boilerplate` |
| `OTEL_SAMPLING_PROBABILITY` | No | `0.1` | Trace sampling rate — 10% by default; set to `1.0` for full sampling during development |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | `http://localhost:4318/v1/traces` | OTLP HTTP traces endpoint |

### Spring Boot Actuator

| Property | Required | Default | Description |
|---|---|---|---|
| `management.endpoints.web.exposure.include` | No | `health,info,metrics,prometheus` | Exposed actuator endpoints |
| `management.endpoint.health.show-details` | No | `when_authorized` | Health details are only shown to authorized callers |

---

## Production Checklist

Before deploying to production:

- [ ] `JWT_PRIVATE_KEY_PATH` and `JWT_PUBLIC_KEY_PATH` point to a unique Ed25519 key pair generated for this environment — never reused across environments
- [ ] `SPRING_DATASOURCE_URL` uses a TLS JDBC URL (`?ssl=true&sslmode=require`)
- [ ] `SPRING_DATASOURCE_PASSWORD` is injected via a secrets manager
- [ ] `SPRING_DATA_REDIS_URL` uses a password-protected Redis instance
- [ ] `CORS_ALLOWED_ORIGINS` lists only your actual frontend domains
- [ ] `LOGGING_LEVEL_ROOT` is `INFO` or `WARN` — never `DEBUG` or `TRACE`
- [ ] `spring.jpa.show-sql` is `false` (default) — never `true` in production
- [ ] `management.endpoint.health.show-details` is `never` in production
- [ ] All secrets are injected via a secrets manager — never in `application.yml` committed to source control
