# Testing

## Philosophy

Tests are written before implementation (TDD). The test suite is organized in two strict tiers: unit tests that run in milliseconds with no external dependencies, and integration tests that run against real infrastructure.

The in-memory adapter exists precisely to make the entire business logic testable without Docker, a database, or any network call.

---

## Running Tests

```bash
# Unit tests only (fast, no external deps)
./gradlew test

# Integration tests (requires Docker for Testcontainers)
./gradlew test -Pintegration

# Specific test class
./gradlew test --tests "com.enterprise.boilerplate.application.usecase.RegisterUserUseCaseTest"

# Coverage report (JaCoCo)
./gradlew jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Architecture tests
./gradlew test --tests "com.enterprise.boilerplate.ArchitectureTest"
```

---

## Test Structure

```
src/test/java/com/enterprise/boilerplate/
│
├── domain/
│   ├── valueobject/
│   │   ├── EmailTest.java              # value object tests
│   │   └── PasswordHashTest.java
│   └── entity/
│       └── UserTest.java               # entity invariant tests
│
├── application/
│   └── usecase/
│       ├── RegisterUserUseCaseTest.java  # use case tests with Mockito
│       └── LoginUserUseCaseTest.java
│
├── infrastructure/
│   └── persistence/
│       └── postgres/
│           └── PostgresUserRepositoryIT.java  # Testcontainers integration test
│
└── ArchitectureTest.java               # ArchUnit dependency rule enforcement
```

---

## Unit Tests

Unit tests use JUnit 5 and Mockito. They cover:

- Value object construction (valid and invalid inputs)
- Entity invariant enforcement
- Use case business logic (success and failure paths)

Repository and port dependencies are replaced with Mockito mocks generated from the interface definitions. No Spring context is loaded.

### Example — Value Object

```java
class EmailTest {

    @Test
    void validEmailIsAccepted() {
        assertThatCode(() -> new Email("user@example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void emailWithoutAtSignThrowsDomainException() {
        assertThatThrownBy(() -> new Email("notanemail"))
            .isInstanceOf(InvalidEmailException.class);
    }
}
```

### Example — Use Case with Mockito

```java
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordHasherPort passwordHasher;

    @InjectMocks RegisterUserUseCase useCase;

    @Test
    void savesNewUserWhenEmailIsNotTaken() {
        given(userRepository.findByEmail(any())).willReturn(Optional.empty());
        given(passwordHasher.hash(anyString())).willReturn(new PasswordHash("$argon2id$..."));

        assertThatCode(() -> useCase.execute(validCommand())).doesNotThrowAnyException();

        then(userRepository).should().save(any(User.class));
    }

    @Test
    void throwsWhenEmailIsAlreadyRegistered() {
        given(userRepository.findByEmail(any())).willReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> useCase.execute(validCommand()))
            .isInstanceOf(UserAlreadyExistsException.class);

        then(userRepository).should(never()).save(any());
    }
}
```

---

## Integration Tests

Integration tests use the `IT` suffix convention and run with Testcontainers. A PostgreSQL container is started once per test class via `@Testcontainers` + `@Container`.

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PostgresUserRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PostgresUserRepository repository;

    @Test
    @Transactional
    void savesAndRetrievesUserByEmail() {
        User user = UserFactory.create();
        repository.save(user);

        Optional<User> found = repository.findByEmail(user.email());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(user.id());
    }
}
```

---

## Architecture Tests (ArchUnit)

ArchUnit rules run as standard JUnit 5 tests and fail the build if any dependency rule is violated.

```java
@AnalyzeClasses(packages = "com.enterprise.boilerplate")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..interfaces..", "org.springframework..");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");
}
```

---

## TDD Workflow

1. Write a failing test that describes the expected behavior
2. Run `./gradlew test` — confirm it fails for the right reason
3. Write the minimum implementation to make it pass
4. Run `./gradlew test` — confirm green
5. Refactor under green

Never write implementation code without a failing test first.

---

## Coverage Target

| Layer | Target |
|---|---|
| Domain (entities + value objects) | 100% |
| Application (use cases) | 100% |
| Infrastructure adapters | 80%+ |
| HTTP controllers | 70%+ (covered by integration tests) |

JaCoCo enforces minimum thresholds in `build.gradle.kts`. Builds fail if coverage drops below the defined limits.
