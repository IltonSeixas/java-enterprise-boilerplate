# Security

## Threat Model

This boilerplate is designed for multi-tenant web APIs exposed to the public internet. The primary threats addressed are:

- Credential stuffing and brute-force attacks
- Session hijacking and token theft
- Injection attacks (SQL, command)
- Information disclosure via error messages or logs
- Denial of service via resource exhaustion

---

## Password Hashing — Argon2id

All passwords are hashed using **Argon2id** via `Spring Security Crypto` backed by **BouncyCastle**.

bcrypt and scrypt are not used.

### Parameters

```java
Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(
    16,     // salt length (bytes)
    32,     // hash length (bytes)
    4,      // parallelism
    65536,  // memory cost (KB) — 64 MB
    3       // iterations
);
```

These parameters meet the OWASP minimum recommendations. Adjust upward based on your hardware profile and acceptable latency budget.

### Salt

A cryptographically random 16-byte salt is generated per-hash via `SecureRandom`. The salt is embedded in the encoded output — never stored separately.

### Verification

`encoder.matches(rawPassword, encodedPassword)` uses constant-time comparison internally. Never use `String.equals()` for hash comparison.

---

## Authentication

### Access Token (JWT HS256)

- Algorithm: HS256 (HMAC-SHA256) via `jjwt` (`io.jsonwebtoken`)
- TTL: 15 minutes
- Claims: `sub` (user ID), `iat`, `exp`, `jti` (unique token ID), `roles`
- Storage: in-memory on the client — never in `localStorage` or cookies
- Validation: signature + expiry + issuer checked on every authenticated request via Spring Security filter

### Refresh Token

- Format: opaque UUID v4 via `UUID.randomUUID()` (JDK built-in)
- Storage: server-side in Redis with TTL 7 days
- Transport: HttpOnly, Secure, SameSite=Strict cookie via `HttpServletResponse`
- Rotation: a new refresh token is issued on every use; the old one is immediately invalidated
- Revocation: evicting the Redis key invalidates the session instantly

### Token Revocation

Access tokens cannot be revoked before expiry (stateless by design). The 15-minute TTL limits the exposure window. If immediate revocation is required, implement a short-lived Redis blocklist for `jti` values.

---

## Rate Limiting

This boilerplate does not ship an in-process rate limiter. In production, place the application behind an edge layer (API gateway, reverse proxy, or CDN) that enforces per-IP and per-account request limits — particularly on authentication endpoints, to mitigate credential stuffing and brute-force attacks.

On limit exceeded, the edge layer should return `429 Too Many Requests` with a `Retry-After` header.

---

## Security Headers

Applied globally via Spring Security's `HttpSecurity` configuration on every response:

| Header | Value |
|---|---|
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | `default-src 'none'` (API — no HTML served) |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` |

---

## CORS

CORS is configured with an explicit allow-list via Spring Security. The wildcard `*` is never permitted in production.

```java
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(List.of(env.getAllowedOrigins())); // from environment
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
config.setAllowCredentials(true);
```

---

## Input Validation

All inputs are validated at the controller boundary using Jakarta Bean Validation before reaching any use case. Invalid input returns `400 Bad Request` with a structured error body via `@RestControllerAdvice` — never a stack trace.

```java
public record RegisterUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 12, max = 128) String password,
    @NotBlank @Size(max = 100) String name
) {}
```

Domain-level invariants are re-enforced inside value object constructors regardless of what the controller does. The domain is the last line of defense.

---

## SQL Injection Prevention

All database queries use Spring Data JPA with parameterized JPQL or `@Query` with named parameters. Native SQL string concatenation is never used.

```java
@Query("SELECT u FROM UserJpaEntity u WHERE u.email = :email")
Optional<UserJpaEntity> findByEmail(@Param("email") String email);
```

---

## Sensitive Data

- Passwords are never logged, never returned in API responses, and never stored in plain text
- Tokens are never logged
- Error responses to clients contain a message and an error code — never internal details, stack traces, or exception class names
- `logging.level.root` must never be set to `DEBUG` or `TRACE` in production
- Spring Boot's `spring.jpa.show-sql` must be `false` in production (would log query parameters)

---

## Dependency Auditing

OWASP Dependency-Check runs on every CI push against the National Vulnerability Database.

```bash
./mvnw org.owasp:dependency-check-maven:check
```

Review the generated HTML report. Every transitive dependency is a potential attack surface. Pin or exclude affected artifacts when CVEs are found.
