# ADR-0003: Stateless JWT Access Tokens with Server-Side Refresh Tokens

**Date:** 2026-06-06  
**Status:** Accepted

---

## Context

Spring Security supports both stateful sessions and stateless JWT. The choice affects scalability, revocability, and operational complexity.

## Decision

Hybrid model: stateless JWT access token (see [ADR-0005](0005-eddsa-jwt-signing.md) for the signing algorithm; TTL 15 min) via `jjwt` (`io.jsonwebtoken`) + opaque UUID refresh token stored in Redis (TTL 7 days, rotated on use, HttpOnly cookie).

## Consequences

**Positive:**
- Hot path requires no database lookup — signature verification only.
- Sessions are revocable by evicting the Redis key.
- Spring Security's stateless session configuration (`SessionCreationPolicy.STATELESS`) aligns naturally.

**Negative:**
- Access tokens cannot be revoked within their 15-minute window without a `jti` blocklist.

## Alternatives Considered

- **Spring Session with Redis** — full server-side session; lookup on every request; less scalable.
- **Pure stateless JWT** — no revocation; unacceptable for a security boilerplate.
- **Spring Authorization Server (OAuth2)** — correct for multi-service federated auth; out of scope for a self-contained boilerplate.
