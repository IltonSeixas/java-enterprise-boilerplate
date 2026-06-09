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

All passwords are hashed using **Argon2id**, computed directly through **BouncyCastle**'s `Argon2BytesGenerator`/`Argon2Parameters` — not through Spring Security Crypto's `Argon2PasswordEncoder`. `Argon2PasswordHasher` owns PHC-string encoding and decoding so the rest of the codebase only ever sees the `PasswordHasherPort` abstraction.

bcrypt and scrypt are not used.

### Parameters

```java
Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
    .withSalt(salt)
    .withParallelism(4)     // p_cost: 4 parallel lanes
    .withMemoryAsKB(65536)  // m_cost: 64 MB
    .withIterations(3)      // t_cost: 3 iterations
    .build();
```

These parameters meet the OWASP minimum recommendations. Adjust upward based on your hardware profile and acceptable latency budget.

### Salt

A cryptographically random 16-byte salt is generated per-hash via `SecureRandom`. The salt is embedded in the PHC-formatted output string — never stored separately.

### Verification

Verification re-derives the hash with the stored salt and parameters, then compares it to the stored digest in constant time. Never use `String.equals()` for hash comparison.

---

## Authentication

### Access Token (JWT HS256)

- Algorithm: HS256 (HMAC-SHA256) via `jjwt` (`io.jsonwebtoken`)
- TTL: 15 minutes (`JWT_ACCESS_EXPIRY_MINUTES`)
- Claims: `sub` (user ID), `role`, `iat`, `exp`
- Transport: returned in the JSON response body (`access_token`); the client is responsible for storage and for sending it as `Authorization: Bearer <token>`
- Validation: signature + expiry checked on every authenticated request by `JwtAuthenticationFilter` (REST) and the gRPC interceptor

### Refresh Token

- Format: opaque UUID v4 via `UUID.randomUUID()` (JDK built-in)
- Storage: server-side in Redis with TTL 7 days (`JWT_REFRESH_EXPIRY_DAYS`), via `RedisTokenStore`
- Transport: `HttpOnly`, `Secure`, `SameSite=Strict` cookie set by `AuthController` (`ResponseCookie`) — never exposed to client-side JavaScript
- Rotation: a new refresh token is issued on every use; the old one is immediately invalidated
- Revocation: evicting the Redis key invalidates the session instantly

### Token Revocation

Access tokens cannot be revoked before expiry (stateless by design). The 15-minute TTL limits the exposure window. Refresh tokens, by contrast, are revocable instantly because they are stored server-side in Redis.

---

## Rate Limiting

Authentication endpoints are protected by an in-process per-IP rate limiter implemented in `AuthRateLimitFilter` (a `@Component`/`OncePerRequestFilter` with `@Order(1)`). It applies to every path under `/api/v1/auth`.

```
Auth endpoints: 10 requests / 60 seconds per IP
```

The limiter uses a fixed-window counter per IP stored in a `ConcurrentHashMap`. The window resets after 60 seconds from the first request in that window. On limit exceeded, the filter short-circuits the chain and returns `429 Too Many Requests` before Spring Security processes the request.

`X-Forwarded-For` is respected so the real client IP is used when the application runs behind a reverse proxy.

For all other paths, rate limiting is delegated to the edge layer (API gateway, reverse proxy, or CDN) — the in-process limiter is scoped to auth to mitigate credential stuffing and brute-force attacks without introducing a broad dependency.

---

## Security Headers

Applied globally via Spring Security's `HttpSecurity` configuration on every response:

| Header | Value |
|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

---

## CORS

CORS is configured with an explicit allow-list via Spring Security. The wildcard `*` is never permitted in production.

```java
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(List.of(env.getAllowedOrigins())); // from environment
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
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
