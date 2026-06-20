# ADR-0005: EdDSA (Ed25519) for JWT Access Token Signing

**Date:** 2026-06-19  
**Status:** Accepted

---

## Context

[ADR-0003](0003-jwt-stateless-auth.md) established stateless JWT access tokens, originally signed with HS256 (HMAC-SHA256). HS256 is symmetric: the same secret signs and verifies tokens, so every service that needs to verify a token must hold the secret that can also forge one. That is a reasonable constraint for a single-service boilerplate, but it does not generalize cleanly to a distributed system where multiple services validate tokens issued by one authentication service.

## Decision

Sign access tokens with **EdDSA (Ed25519)** instead of HS256, using an asymmetric key pair:
- `JWT_PRIVATE_KEY_PATH` — Ed25519 private key (PKCS#8 PEM), used only by the service that issues tokens.
- `JWT_PUBLIC_KEY_PATH` — the matching public key, used to verify tokens. Safe to distribute to other services without granting them the ability to forge tokens.

Keys are generated with standard OpenSSL tooling:

```bash
openssl genpkey -algorithm ed25519 -out jwt_private.pem
openssl pkey -in jwt_private.pem -pubout -out jwt_public.pem
```

`jjwt` (0.13.0) supports EdDSA natively via `Jwts.SIG.EdDSA`, auto-detected from the key type passed to `signWith`/`verifyWith`. PEM parsing uses the JDK's built-in `java.security.KeyFactory` with algorithm `"Ed25519"` (supported natively since JDK 15) against the DER bytes decoded from the PEM — no PEM-parsing dependency (e.g. BouncyCastle's `PEMParser`) is required.

`JwtTokenService` loads and parses the key files lazily, on first use, rather than eagerly in the constructor. This keeps Spring integration tests that boot the full application context (e.g. `PostgresUserRepositoryIntegrationTest`) working without needing to provide real JWT key files when they never exercise the JWT flow.

## Consequences

**Positive:**
- Verification no longer requires the signing secret — a future second service could hold only the public key and verify tokens independently.
- Ed25519 signatures are smaller and faster to verify than RSA, and avoid the timing-attack surface historically associated with some ECDSA implementations.
- No new runtime dependency: `jjwt` already supports EdDSA, and key parsing uses only the JDK standard library.

**Negative:**
- Breaking change: tokens issued under HS256 cannot be verified by the new code and vice versa. There is no dual-algorithm transition period — this is a reference boilerplate, not a production system with live sessions to migrate.
- Key management is marginally more involved than a single secret string: two PEM files must be generated and distributed instead of one random value.

## Alternatives Considered

- **ES256 (ECDSA P-256)** — also asymmetric and widely supported, but slower to verify than Ed25519 and has a track record of subtle implementation bugs (e.g., nonce reuse) in some libraries across languages. Ed25519's design avoids that entire class of failure mode.
- **BouncyCastle `PEMParser` for key loading** — would work, and BouncyCastle is already a dependency (used for Argon2id), but the JDK's native `KeyFactory` support for Ed25519 makes that an unnecessary dependency for this specific task.
- **Keep HS256, rely on a shared secret distributed out-of-band** — simpler for a single service, but does not demonstrate the asymmetric pattern that becomes necessary once more than one service needs to verify tokens.
