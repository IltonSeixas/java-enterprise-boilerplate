# Contributing

Contributions are welcome. Please read this document before opening a pull request.

---

## Prerequisites

- Java 21+ (recommended: install via [SDKMAN](https://sdkman.io/): `sdk install java 21-tem`)
- Docker (for integration tests via Testcontainers)

---

## Development Workflow

```bash
# Build (skipping tests)
./gradlew build -x test

# Compile only
./gradlew compileJava

# Run all unit tests
./gradlew test

# Run architecture tests
./gradlew test --tests "com.enterprise.boilerplate.ArchitectureTest"

# Run integration tests (requires Docker)
./gradlew test -Pintegration

# Coverage report
./gradlew jacocoTestReport

# Dependency vulnerability check
./gradlew dependencyCheckAnalyze

# Check formatting (Spotless)
./gradlew spotlessCheck

# Apply formatting
./gradlew spotlessApply
```

All of the above run automatically in CI on every pull request. A PR will not be merged if any of these steps fail.

---

## Code Standards

### Architecture

- Never import Spring, JPA, or any infrastructure class from `domain/` or `application/`
- This rule is enforced by ArchUnit — violations fail the build automatically
- Every new use case must have a corresponding `*Test.java` file
- Every new value object must validate its invariants in the constructor and have tests for both valid and invalid inputs

### Style

- Code formatting via **Spotless** with Google Java Format — run `./gradlew spotlessApply` before committing
- No raw `System.out.println` — use `SLF4J` via `@Slf4j`
- No checked exceptions in domain or application layers — use sealed exception hierarchies
- No comments that explain *what* the code does — only *why* when non-obvious
- Prefer Java Records for DTOs and value objects where applicable

### Tests

- New behavior requires a test written first (TDD)
- Mock repositories and ports via Mockito — never load a Spring context in unit tests
- Integration tests must be annotated with `@Transactional` or clean up their own data

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
