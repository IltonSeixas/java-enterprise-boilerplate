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
│   ├── repository/
│   │   └── UserRepository.java            # Interface: the only contract infra must fulfill
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
│   │   └── TokenServicePort.java          # Interface: issue, validate and rotate JWTs
│   └── dto/
│       ├── RegisterUserRequest.java       # Records + Jakarta validation
│       ├── LoginRequest.java
│       ├── RefreshTokenRequest.java
│       ├── ChangePasswordRequest.java
│       ├── UpdateProfileRequest.java
│       ├── AuthResponse.java
│       └── UserResponse.java
│
├── infrastructure/
│   ├── persistence/
│   │   ├── memory/
│   │   │   └── InMemoryUserRepository.java   # @Profile("inmemory") — default, zero-config
│   │   └── postgres/
│   │       ├── JpaUserRepository.java        # Spring Data JPA interface
│   │       ├── UserJpaEntity.java            # JPA entity (separate from domain entity)
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
│   │   └── SecurityConfig.java               # CORS, security headers, filter chain
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
    boolean existsByEmail(Email email);
    boolean hasOwner();
    void saveFirstOwner(User user);
}
```

The interface lives in `domain/repository/` — owned by the domain, not by the infrastructure that implements it. The JPA entity is a separate class in `infrastructure/persistence/postgres/`.

---

## Application Layer

Each use case is a `@Service`-annotated class (Spring manages lifecycle) that receives its dependencies via constructor injection. It exposes a single public method. No Spring Data, JPA, or any infrastructure import appears here.

```java
@Service
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

---

## Infrastructure Layer

Classes in `infrastructure/` implement domain/application interfaces. They are the only place where JPA annotations, Spring Data, Redis, or BouncyCastle are imported. The `UserJpaEntity` is a separate class from the `User` domain entity — they are mapped explicitly to avoid polluting the domain with persistence concerns.

The in-memory adapter uses `ConcurrentHashMap` and is production-equivalent for the domain — it satisfies the same interface contract.

---

## Architecture Enforcement

The dependency rule — domain and application code must never import Spring, JPA, gRPC, or any other infrastructure concern — is enforced through code review and package boundaries rather than an automated architecture-test tool. Keeping `domain/` and `application/` free of framework imports is a non-negotiable contribution standard (see [contributing.md](contributing.md)); any pull request that violates it is rejected at review time.
