# Testing

## Philosophy

Tests are written before implementation (TDD). The test suite is organized in two strict tiers: unit tests that run in milliseconds with no external dependencies, and integration tests that run against real infrastructure.

The in-memory adapter exists precisely to make the entire business logic testable without Docker, a database, or any network call.

---

## Running Tests

```bash
# Unit tests only (fast, no external deps — this is the default)
./mvnw test

# Integration tests (tagged with @Tag("integration"), excluded by default)
./mvnw test -Dgroups=integration -DexcludedGroups=

# Specific test class
./mvnw test -Dtest=RegisterUserUseCaseTest
```

The Surefire plugin is configured with `<excludedGroups>integration</excludedGroups>` in `pom.xml`, so `./mvnw test` runs only unit tests by default. Passing `-Dgroups=integration -DexcludedGroups=` overrides both properties to run the integration suite instead.

---

## Test Structure

```
src/test/java/com/enterprise/boilerplate/
│
├── domain/
│   ├── valueobject/
│   │   └── EmailTest.java                    # value object tests
│   └── entity/
│       └── UserTest.java                     # entity invariant tests
│
├── domain/
│   └── audit/
│       └── AuditEventTest.java               # audit event construction and invariants
│
├── application/
│   └── usecase/
│       ├── RegisterUserUseCaseTest.java       # use case tests with Mockito
│       ├── LoginUserUseCaseTest.java
│       ├── RefreshTokenUseCaseTest.java
│       ├── GetUserUseCaseTest.java
│       ├── ListUsersUseCaseTest.java
│       ├── UpdateProfileUseCaseTest.java      # includes AuditPort mock — verifies PROFILE_UPDATED is recorded
│       ├── ChangePasswordUseCaseTest.java
│       ├── ChangeUserRoleUseCaseTest.java
│       └── LogoutUseCaseTest.java
│
├── infrastructure/
│   ├── audit/
│   │   ├── InMemoryAuditLogTest.java
│   │   ├── AuditLogHealthIndicatorTest.java
│   │   └── PostgresAuditLogTest.java         # verifies graceful degradation on JPA failure
│   ├── cache/
│   │   ├── RedisRateLimitStoreTest.java       # Lua script, null-safety, TTL forwarding, fallback -1
│   │   └── RedisTokenStoreTest.java          # set/get/delete delegation to RedisTemplate
│   ├── health/
│   │   ├── GrpcServerHealthIndicatorTest.java
│   │   └── JwtKeysHealthIndicatorTest.java
│   ├── persistence/
│   │   ├── InMemoryUserRepositoryTest.java    # in-memory adapter (including email uniqueness guard)
│   │   └── postgres/
│   │       ├── FlywayConcurrentMigrationIntegrationTest.java  # @Tag("integration") — 8 concurrent replicas
│   │       ├── PostgresUserRepositoryIntegrationTest.java     # @Tag("integration") — Testcontainers PostgreSQL
│   │       └── UserSpecificationsTest.java    # role/active/nameContains predicates, % and _ escaping
│   └── security/
│       ├── Argon2PasswordHasherTest.java      # hash format, unique salts, verify match/mismatch/malformed
│       ├── JwtAuthenticationFilterTest.java   # no header, bad scheme, invalid token, active/inactive user
│       └── JwtTokenServiceTest.java
│
├── interfaces/
│   ├── filter/
│   │   ├── AuthRateLimitFilterTest.java
│   │   └── RequestLoggingFilterTest.java      # chain delegation, log-injection sanitization
│   ├── grpc/
│   │   ├── GrpcAuthenticationInterceptorTest.java  # public passthrough, missing/malformed/invalid token
│   │   ├── GrpcExceptionMapperTest.java            # all 11 exception → gRPC Status mappings
│   │   ├── GrpcRateLimitInterceptorTest.java       # rate-limit for Register/Login/RefreshToken, fallback, forwarded-for
│   │   └── GrpcServerIntegrationTest.java          # @Tag("integration") — in-process gRPC suite
│   └── rest/
│       ├── AuthControllerTest.java           # register/login/refresh/logout — happy and error paths
│       ├── GlobalExceptionHandlerTest.java   # 401/400 mappings for domain exceptions
│       ├── UserControllerTest.java           # all user endpoints — happy and error paths, sort params
│       └── UserControllerValidationTest.java
│
└── architecture/
    └── LayeredArchitectureTest.java           # ArchUnit — enforces the Clean Architecture dependency rule
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

Integration tests are annotated with `@Tag("integration")` and excluded from the default `./mvnw test` run via the Surefire `excludedGroups` configuration. `GrpcServerIntegrationTest` exercises the gRPC layer end to end over an in-process transport, wiring the real use cases against the in-memory adapters — no external infrastructure required.

```java
@Tag("integration")
class GrpcServerIntegrationTest {

    private static final String SERVER_NAME = "grpc-integration-" + UUID.randomUUID();

    private Server server;
    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub authStub;

    @BeforeEach
    void setUp() throws Exception {
        UserRepository userRepository = new InMemoryUserRepository();
        server = InProcessServerBuilder.forName(SERVER_NAME)
            .directExecutor()
            .addService(new AuthGrpcService(registerUseCase, loginUseCase, refreshUseCase, logoutUseCase, tokenService))
            .build()
            .start();

        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        authStub = AuthServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void registersAndAuthenticatesUserOverGrpc() {
        authStub.register(RegisterRequest.newBuilder()
            .setEmail("user@example.com")
            .setPassword("a-strong-password")
            .setName("Test User")
            .build());

        AuthResponse response = authStub.login(LoginRequest.newBuilder()
            .setEmail("user@example.com")
            .setPassword("a-strong-password")
            .build());

        assertThat(response.getAccessToken()).isNotBlank();
    }
}
```

This in-process approach mirrors the bufconn/loopback pattern used by the Go and TypeScript boilerplates' gRPC integration suites — fast, deterministic, and free of Docker or network dependencies.

`PostgresUserRepositoryIntegrationTest` covers the PostgreSQL adapter the same way: it is also `@Tag("integration")`, but instead of an in-process fake it uses Testcontainers (`org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`, pinned via the `testcontainers.version` property in `pom.xml`) to start a real, disposable PostgreSQL container per test class via `@Testcontainers`/`@Container`. This requires a working Docker daemon locally or in CI, but no manual database setup — the container's JDBC URL is wired into the Spring context at runtime via `@DynamicPropertySource`.

---

## Architecture Tests

`LayeredArchitectureTest` uses [ArchUnit](https://www.archunit.org/) to enforce the dependency rule from [ADR-0001](adr/0001-clean-architecture.md) as a real, automatically-run test rather than a convention checked only in review — see [ADR-0006](adr/0006-archunit-architecture-tests.md) and [ADR-0007](adr/0007-zero-spring-in-application-layer.md). It runs as part of the default `./mvnw test` step and fails the build if:

- `domain/` or `application/` depends on Spring, JPA/Hibernate, gRPC, JJWT, BouncyCastle, or a Redis client — including Spring's own DI annotations (`@Service`, `@Value`); use cases must be plain classes wired through `infrastructure/config/UseCaseConfig`
- any layer is accessed from a layer further out (e.g. `domain/` reaching into `infrastructure/`)
- a class named `*UseCase` lives outside `application.usecase`
- a type under `application.port` is not an interface

---

## TDD Workflow

1. Write a failing test that describes the expected behavior
2. Run `./mvnw test` — confirm it fails for the right reason
3. Write the minimum implementation to make it pass
4. Run `./mvnw test` — confirm green
5. Refactor under green

Never write implementation code without a failing test first.

---

## Coverage

```bash
./mvnw test jacoco:report
# Report written to target/site/jacoco/index.html
```

Current coverage (186 unit tests, excluding `@Tag("integration")`):

| Metric | Coverage |
|---|---|
| Line | 78% |
| Branch | 77% |
| Method | 76% |

| Layer | Expectation |
|---|---|
| Domain (entities + value objects) | Every invariant covered, valid and invalid paths |
| Application (use cases) | Every success and failure path covered with mocked ports |
| Infrastructure adapters | Behavior verified against the port contract |
| Interfaces (REST + gRPC) | Controllers and interceptors covered with MockMvc / in-process gRPC stubs |

There is no enforced coverage threshold wired into the build — coverage is maintained through the TDD workflow and code review. Integration tests (`@Tag("integration")`) are excluded from the JaCoCo report by default because they require Docker; run them separately and recheck if you add infrastructure adapters.
