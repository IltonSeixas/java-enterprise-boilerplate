# Contributing

Contributions are welcome. Please read this document before opening a pull request.

---

## Prerequisites

- Java 21+ (recommended: install via [SDKMAN](https://sdkman.io/): `sdk install java 21-tem`)
- No local Maven installation required — use the bundled `./mvnw` wrapper

---

## Development Workflow

```bash
# Compile only
./mvnw compile test-compile

# Run unit tests
./mvnw test

# Run integration tests (tagged with @Tag("integration"))
./mvnw test -Dgroups=integration -DexcludedGroups=

# Package the application (skipping tests)
./mvnw package -DskipTests

# Dependency vulnerability check
./mvnw org.owasp:dependency-check-maven:check
```

All of the above run automatically in CI on every pull request. A PR will not be merged if any of these steps fail.

---

## Required GitHub Secrets

The "Security Audit" CI job runs OWASP `dependency-check`, which queries the NVD (National Vulnerability Database) to refresh its local vulnerability data on every run. Without an API key, NVD rate-limits requests so heavily that the download of its full record set can take hours or never complete.

Maintainers must configure an **`NVD_API_KEY`** secret, obtained for free from [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key), in **both** of the following locations under repository **Settings → Secrets and variables**:

- **Actions** — used by workflow runs triggered from same-repo branches and manually-opened PRs
- **Dependabot** — used by workflow runs triggered from Dependabot PRs

These are two separate secret stores. Dependabot-triggered workflow runs do **not** receive Actions secrets, so the key must be added to both tabs or the audit job will hang on every Dependabot PR.

---

## Code Standards

### Architecture

- `domain/` and `application/` must never depend on any framework — not Spring, not JPA/Hibernate, not gRPC, not JJWT, not BouncyCastle, not a Redis client. Both layers are constructed with `new`, never injected via `@Service`/`@Value`/`@Component`.
- Use cases are plain classes with a constructor and an `execute(...)` method, nothing else. Spring wires them by calling that constructor explicitly from an `@Bean` factory method in an infrastructure `@Configuration` class (see `UseCaseConfig`) — the use case itself never knows Spring exists.
- Configuration values (timeouts, expiry windows, feature flags) reach use cases as plain constructor parameters, sourced from a typed, `@Validated` `@ConfigurationProperties` record defined in `infrastructure`/`config` — never from `@Value` inside the use case.
- These rules are enforced automatically by `LayeredArchitectureTest` (ArchUnit) — see [ADR-0006](adr/0006-archunit-architecture-tests.md). A PR that violates them fails `./mvnw test`
- Every new use case must have a corresponding `*Test.java` file
- Every new value object must validate its invariants in the constructor and have tests for both valid and invalid inputs
- No generic `BaseService`, `AbstractService`, `Manager`, or catch-all `@Service` class. Each use case is its own class with a single `execute` method and an explicit, narrow set of injected dependencies — never a god class that accumulates every repository and port in the application

### Style

- No raw `System.out.println` — use `SLF4J` via the standard `LoggerFactory.getLogger(...)`
- No checked exceptions in domain or application layers — use sealed exception hierarchies
- No comments that explain *what* the code does — only *why* when non-obvious
- Prefer Java Records for DTOs and value objects where applicable

### Tests

- New behavior requires a test written first (TDD)
- Mock repositories and ports via Mockito — never load a Spring context in unit tests
- Integration tests must be annotated with `@Tag("integration")` and clean up their own data

---

## Pull Request Guidelines

1. Fork the repository and create a branch from `main`
2. Branch naming: `feat/short-description`, `fix/short-description`, `docs/short-description`
3. Keep each PR focused on a single concern
4. Include tests for every behavior change
5. Update relevant documentation in `docs/` if the change affects it
6. Ensure CI passes before requesting review

---

## Commit Convention

```
feat: add password reset use case
fix: correct argon2 parameter configuration
docs: update security configuration reference
refactor: extract email validation into value object
test: add integration test for login flow
chore: update dependencies
```

---

## Reporting Security Vulnerabilities

Do **not** open a public GitHub issue for security vulnerabilities.

Send a private disclosure to [contact@iltonseixas.com](mailto:contact@iltonseixas.com) with:
- A description of the vulnerability
- Steps to reproduce
- Potential impact

You will receive a response within 72 hours.

---

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
