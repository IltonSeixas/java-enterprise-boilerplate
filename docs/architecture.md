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

**Dependency rule:** source code dependencies point inward only. The domain knows nothing about the layers outside it. This is enforced automatically by ArchUnit tests on every build.

---

## Directory Structure

```
src/main/java/com/enterprise/boilerplate/
│
├── domain/
│   ├── entity/
│   │   └── User.java                  # Aggregate root (plain Java, no JPA annotations)
│   ├── valueobject/
│   │   ├── Email.java                 # Record — validated on construction
│   │   ├── PasswordHash.java          # Opaque wrapper (record)
│   │   └── UserId.java                # UUID newtype (record)
│   ├── repository/
│   │   └── UserRepository.java        # Interface: the only contract infra must fulfill
│   └── exception/
│       └── DomainException.java       # Sealed class hierarchy
│
├── application/
│   ├── usecase/
│   │   ├── RegisterUserUseCase.java
│   │   ├── LoginUserUseCase.java
│   │   ├── RefreshTokenUseCase.java
│   │   └── LogoutUserUseCase.java
│   ├── port/
│   │   ├── in/
│   │   │   ├── RegisterUserCommand.java   # Record (input port)
│   │   │   └── LoginUserCommand.java
│   │   └── out/
│   │       ├── PasswordHasherPort.java    # Interface: hash + verify
│   │       └── TokenIssuerPort.java       # Interface: issue + validate JWT
│   └── dto/
│       ├── RegisterUserRequest.java       # Record + Jakarta validation
│       └── AuthResponse.java             # Record
│
├── infrastructure/
│   ├── persistence/
│   │   ├── memory/
│   │   │   └── InMemoryUserRepository.java
│   │   └── postgres/
│   │       ├── JpaUserRepository.java     # Spring Data JPA interface
│   │       ├── UserJpaEntity.java         # JPA entity (separate from domain entity)
│   │       └── PostgresUserRepository.java
│   ├── security/
│   │   ├── Argon2PasswordHasher.java
│   │   └── JwtService.java
│   ├── cache/
│   │   └── RedisTokenStore.java
│   └── telemetry/
│       └── ObservabilityConfig.java
│
└── interfaces/
    ├── rest/
    │   ├── AuthController.java
    │   ├── UserController.java
    │   ├── GlobalExceptionHandler.java    # @RestControllerAdvice
    │   └── filter/
    │       ├── JwtAuthFilter.java
    │       ├── RateLimitFilter.java
    │       └── SecurityHeadersFilter.java
    └── grpc/
        └── UserGrpcService.java
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
    Optional<User> findByEmail(Email email);
    void save(User user);
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

    public void execute(RegisterUserCommand command) {
        // 1. validate command (Bean Validation fired by caller)
        // 2. check uniqueness
        // 3. hash password
        // 4. construct domain entity
        // 5. persist
    }
}
```

---

## Infrastructure Layer

Classes in `infrastructure/` implement domain/application interfaces. They are the only place where JPA annotations, Spring Data, Redis, or BouncyCastle are imported. The `UserJpaEntity` is a separate class from the `User` domain entity — they are mapped explicitly to avoid polluting the domain with persistence concerns.

The in-memory adapter uses `ConcurrentHashMap` and is production-equivalent for the domain — it satisfies the same interface contract.

---

## Architecture Enforcement (ArchUnit)

`ArchitectureTest.java` runs on every build and fails if the dependency rules are violated:

```java
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..infrastructure..", "..interfaces..", "org.springframework..")
    .check(importedClasses);
```

This makes architectural drift a compile-time (test-time) failure, not a code review finding.
