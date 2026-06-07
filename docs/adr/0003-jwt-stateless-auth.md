# ADR-0003: Stateless JWT Access Tokens with Server-Side Refresh Tokens

**Date:** 2026-06-06  
**Status:** Accepted

---

## Context

Spring Security supports both stateful sessions and stateless JWT. The choice affects scalability, revocability, and operational complexity.

## Decision

Hybrid model: stateless JWT **HS256** access token (TTL 15 min) via `jjwt` (`io.jsonwebtoken`) + opaque UUID refresh token stored in Redis (TTL 7 days, rotated on use, HttpOnly cookie).

HS256 (symmetric, HMAC-SHA256) keeps the boilerplate dependency-light and easy to configure via a single `JWT_SECRET` environment variable. Services that need to verify tokens without holding the signing secret can switch to an asymmetric algorithm (RS256/ES256) by changing the key material in `JwtTokenService` — the rest of the auth flow is unaffected.

## Consequences

**Positive:**
- Hot path requires no database lookup — HMAC signature verification only.
- Sessions are revocable by evicting the Redis key.
- A single `JWT_SECRET` environment variable is the entire key management surface — no key pairs to generate, rotate, or distribute.
- Spring Security's stateless session configuration (`SessionCreationPolicy.STATELESS`) aligns naturally.

**Negative:**
- The signing secret must be shared with every service that verifies tokens, widening the blast radius of a leak compared to an asymmetric scheme.
- Access tokens cannot be revoked within their 15-minute window without a `jti` blocklist.

## Alternatives Considered

- **Spring Session with Redis** — full server-side session; lookup on every request; less scalable.
- **Pure stateless JWT** — no revocation; unacceptable for a security boilerplate.
- **Spring Authorization Server (OAuth2)** — correct for multi-service federated auth; out of scope for a self-contained boilerplate.
