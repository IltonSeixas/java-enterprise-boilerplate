# ADR-0003: Stateless JWT Access Tokens with Server-Side Refresh Tokens

**Date:** 2024-01-01  
**Status:** Accepted

---

## Context

Spring Security supports both stateful sessions and stateless JWT. The choice affects scalability, revocability, and operational complexity.

## Decision

Hybrid model: stateless JWT **RS256** access token (TTL 15 min) via `nimbus-jose-jwt` + opaque UUID refresh token stored in Redis (TTL 7 days, rotated on use, HttpOnly cookie).

RS256 (asymmetric) is chosen over HS256 for Java because it allows token verification by other services without sharing the signing secret.

## Consequences

**Positive:**
- Hot path requires no database lookup — RSA signature verification only.
- Sessions are revocable by evicting the Redis key.
- RS256 allows resource servers to verify tokens using only the public key.
- Spring Security's stateless session configuration (`SessionCreationPolicy.STATELESS`) aligns naturally.

**Negative:**
- RSA key pair management adds operational overhead compared to a shared HMAC secret.
- Access tokens cannot be revoked within their 15-minute window without a `jti` blocklist.

## Alternatives Considered

- **Spring Session with Redis** — full server-side session; lookup on every request; less scalable.
- **Pure stateless JWT** — no revocation; unacceptable for a security boilerplate.
- **Spring Authorization Server (OAuth2)** — correct for multi-service federated auth; out of scope for a self-contained boilerplate.
