# Architecture

## Overview

This project implements Clean Architecture (also known as Hexagonal Architecture or Ports & Adapters) combined with Domain-Driven Design tactical patterns. The goal is a codebase where the business rules can be read, tested, and reasoned about without any knowledge of Spring, JPA, or any other infrastructure framework.

---

## Layer Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        interfaces/                          │
│           (Spring MVC controllers, gRPC services)           │
├─────────────────────────────────────────────────────────────┤
│                       application/                          │
│              (Use Cases, Input/Output Ports)                │
├─────────────────────────────────────────────────────────────┤
│                         domain/                             │
│          (Entities, Value Objects, Repository Interfaces)   │
├──────────────────────────┬──────────────────────────────────┤
│     infrastructure/      │         infrastructure/          │
│   (PostgreSQL adapter)   │      (In-Memory adapter)         │
└──────────────────────────┴──────────────────────────────────┘
```

**Dependency rule:** source code dependencies point inward only. The domain knows nothing about the layers outside it. This boundary is enforced through package conventions and code review — see [Architecture Enforcement](#architecture-enforcement).

---

## Directory Structure

```
src/main/java/com/enterprise/boilerplate/
│
├── Application.java                       # Spring Boot entry point
│
├── domain/
│   ├── entity/
│   │   └── User.java                      # Aggregate root (plain Java, no JPA annotations)
│   ├── valueobject/
│   │   ├── Email.java                     # Record — validated on construction
│   │   ├── PasswordHash.java              # Opaque wrapper (record)
│   │   └── UserId.java                    # UUID newtype (record)
│   ├── audit/
│   │   ├── AuditEvent.java                # Immutable record: type, actor, target, occurred_at
│   │   └── AuditEventType.java            # Enum: REGISTERED, LOGIN_SUCCESS, LOGIN_FAILURE, PASSWORD_CHANGED, PROFILE_UPDATED, ROLE_CHANGED, LOGOUT, TOKEN_REFRESHED
│   ├── repository/
│   │   ├── UserRepository.java            # Interface: the only contract infra must fulfill
│   │   ├── UserFilter.java                # Value type: role/active/nameContains filter criteria
│   │   ├── PageCriteria.java              # Value type: page/size
│   │   └── UserPage.java                  # Value type: content + totalElements
│   └── exception/
│       ├── DomainException.java           # Sealed exception hierarchy root
│       └── ...                            # InvalidEmailException, UserNotFoundException, etc.
│
├── application/
│   ├── usecase/
│   │   ├── RegisterUserUseCase.java
│   │   ├── LoginUserUseCase.java
│   │   ├── RefreshTokenUseCase.java
│   │   ├── LogoutUseCase.java
│   │   ├── GetUserUseCase.java
│   │   ├── UpdateProfileUseCase.java
│   │   └── ChangePasswordUseCase.java
│   ├── port/out/
│   │   ├── PasswordHasherPort.java        # Interface: hash + verify
│   │   ├── TokenServicePort.java          # Interface: issue, validate and rotate JWTs
│   │   └── AuditPort.java                 # Interface: record(AuditEvent) — implemented by in-memory and PostgreSQL adapters
│   └── dto/
│       ├── RegisterUserRequest.java       # Records + Jakarta validation
│       ├── LoginRequest.java
│       ├── RefreshTokenRequest.java
│       ├── ChangePasswordRequest.java
│       ├── ChangeRoleRequest.java
│       ├── UpdateProfileRequest.java
│       ├── ListUsersRequest.java
│       ├── PageResponse.java
│       ├── AuthResponse.java              # @JsonInclude(NON_NULL) — refreshToken omitted when null
│       └── UserResponse.java
│
├── infrastructure/
│   ├── audit/
│   │   ├── InMemoryAuditLog.java             # @Profile("inmemory") — default; never fails the caller
│   │   ├── PostgresAuditLog.java             # @Profile("postgres") — REQUIRES_NEW transaction; degrades gracefully
│   │   ├── AuditLogJpaEntity.java
│   │   └── AuditLogHealthIndicator.java      # /actuator/health contributor (postgres profile only)
│   ├── persistence/
│   │   ├── memory/
│   │   │   └── InMemoryUserRepository.java   # @Profile("inmemory") — default, zero-config; email uniqueness enforced under lock
│   │   └── postgres/
│   │       ├── JpaUserRepository.java        # Spring Data JPA interface
│   │       ├── UserJpaEntity.java            # JPA entity (separate from domain entity)
│   │       ├── UserSpecifications.java       # JPA Specification for role/active/nameContains filtering
│   │       └── PostgresUserRepository.java   # @Profile("postgres") — adapts JPA to UserRepository
│   ├── security/
│   │   ├── Argon2PasswordHasher.java         # BouncyCastle Argon2id, hand-rolled PHC encoding
│   │   ├── JwtTokenService.java              # Issues/validates JWTs, rotates refresh tokens via Redis
│   │   └── JwtAuthenticationFilter.java      # Validates bearer tokens on every request
│   ├── cache/
│   │   └── RedisTokenStore.java              # Refresh-token storage and rotation
│   ├── config/
│   │   ├── ApplicationConfig.java
│   │   ├── RedisConfig.java
│   │   ├── SecurityConfig.java               # CORS, security headers, filter chain
│   │   └── UseCaseConfig.java                # Explicit composition root — wires all use cases via @Bean
│   ├── health/
│   │   ├── GrpcServerHealthIndicator.java    # /actuator/health + grpc.health.v1.Health
│   │   └── JwtKeysHealthIndicator.java       # /actuator/health — key pair readability
│   └── telemetry/
│       └── OpenTelemetryConfig.java
│
└── interfaces/
    ├── rest/
    │   ├── AuthController.java
    │   ├── UserController.java
    │   └── GlobalExceptionHandler.java        # @RestControllerAdvice
    ├── filter/
    │   └── RequestLoggingFilter.java
    └── grpc/
        ├── AuthGrpcService.java
        ├── UserGrpcService.java
        ├── GrpcAuthenticationInterceptor.java
        ├── GrpcAuthenticatedCaller.java
        ├── GrpcExceptionMapper.java
        └── GrpcMappers.java
```

---

## Domain Layer

### Entities

`User` is the aggregate root — a plain Java class with no Spring or JPA annotations. Construction goes through a static factory method `User.create(...)` that validates invariants and throws `DomainException` on violation. Fields are private and `final`.

### Value Objects

Java Records are used for value objects where applicable. `Email.of("bad")` throws `InvalidEmailException`. Once constructed, the value is always valid.

```java
public record Email(String value) {
    public Email {
        if (value == null || !value.matches("^[^@]+@[^@]+\\.[^@]+$")) {
            throw new InvalidEmailException(value);
        }
    }
}
```

Sealed classes model the domain error hierarchy:

```java
public sealed class DomainException extends RuntimeException
    permits InvalidEmailException, UserAlreadyExistsException, InvalidPasswordException {}
```

### Repository Interface

```java
public interface UserRepository {
    Optional<User> findById(UserId id);
    Optional<User> findByEmail(Email email);
    void save(User user);
    void saveFirstOwner(User user);
    boolean existsByEmail(Email email);
    boolean hasOwner();
    UserPage findAll(UserFilter filter, PageCriteria criteria);
}
```

The interface lives in `domain/repository/` — owned by the domain, not by the infrastructure that implements it. The JPA entity is a separate class in `infrastructure/persistence/postgres/`.

---

## Application Layer

Each use case is a plain Java class — no Spring annotation of any kind — that receives its dependencies via constructor injection. It exposes a single public method. No Spring, JPA, or any infrastructure import appears here. Spring never instantiates it through component scanning; instead, an explicit `@Bean` factory method in `infrastructure/config/UseCaseConfig` calls the constructor directly, so the use case itself has zero awareness that Spring exists.

```java
public class RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasherPort hasher;

    public RegisterUserUseCase(UserRepository users, PasswordHasherPort hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    public UserResponse execute(RegisterUserRequest request) {
        // 1. validate request (Bean Validation fired by the controller)
        // 2. check uniqueness
        // 3. hash password
        // 4. construct domain entity
        // 5. persist
        // 6. map to response DTO
    }
}
```

Configuration values a use case needs (token expiry windows, feature flags) arrive as plain constructor parameters — primitives or domain types, never `@Value` or a Spring type. The caller (`UseCaseConfig`) resolves those values from a typed, `@Validated` `@ConfigurationProperties` record before passing them in.

---

## Infrastructure Layer

Classes in `infrastructure/` implement domain/application interfaces. They are the only place where JPA annotations, Spring Data, Redis, or BouncyCastle are imported. The `UserJpaEntity` is a separate class from the `User` domain entity — they are mapped explicitly to avoid polluting the domain with persistence concerns.

The in-memory adapter uses `ConcurrentHashMap` and is production-equivalent for the domain — it satisfies the same interface contract.

---

## Architecture Enforcement

The dependency rule — domain and application code must never import Spring, JPA, gRPC, or any other infrastructure concern — is enforced automatically by `LayeredArchitectureTest` (ArchUnit), which fails the build if a use case or domain class depends on a framework package. See [ADR-0006](adr/0006-archunit-architecture-tests.md) and [contributing.md](contributing.md) for details.

---

## Build Target

This project runs as a standard JVM application; GraalVM Native Image is deliberately not adopted. The gRPC starter and Resilience4j's AOP-based annotations lack the native-image reachability metadata needed for a build that doesn't silently misbehave at runtime — see [ADR-0010](adr/0010-graalvm-native-image-not-adopted.md) for the full evaluation and revisit conditions.
