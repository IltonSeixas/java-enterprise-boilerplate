# ADR-0002: Use Argon2id for Password Hashing

**Date:** 2026-06-06  
**Status:** Accepted

---

## Context

Spring Security's default `PasswordEncoder` is `BCryptPasswordEncoder`. For a security-focused boilerplate, the choice deserves explicit justification.

## Decision

**Argon2id** computed directly through **BouncyCastle**'s low-level `Argon2BytesGenerator`/`Argon2Parameters` API, wrapped by `Argon2PasswordHasher` behind the `PasswordHasherPort` abstraction. Parameters: 64 MB memory, 3 iterations, 4 lanes (OWASP recommended).

## Consequences

**Positive:**
- Argon2id is the OWASP recommendation since 2019 for new systems.
- BouncyCastle is the reference JVM crypto library — FIPS-certified implementation available.
- Owning the PHC-string encoding ourselves keeps the hashing concern fully behind `PasswordHasherPort`, with no Spring Security `PasswordEncoder` coupling — the domain and application layers never see BouncyCastle types.
- Verification uses `MessageDigest.isEqual` for constant-time comparison, preventing timing attacks.

**Negative:**
- BouncyCastle adds a dependency (~multi-MB JAR) and requires occasional updates as new versions are released.

## Alternatives Considered

- **BCryptPasswordEncoder** — Spring's default; no memory-hardness, 72-byte limit, not recommended for new systems.
- **SCryptPasswordEncoder** — Spring Security also supports it; Argon2id is preferred by OWASP.
- **PBKDF2PasswordEncoder** — FIPS-approved but not memory-hard.
