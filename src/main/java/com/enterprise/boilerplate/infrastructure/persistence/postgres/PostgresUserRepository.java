package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserPage;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Profile("postgres")
class PostgresUserRepository implements UserRepository {

    private final JpaUserRepository jpa;

    @PersistenceContext
    private EntityManager entityManager;

    PostgresUserRepository(JpaUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "postgres")
    @Retry(name = "postgres-read")
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "postgres")
    @Retry(name = "postgres-read")
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "postgres")
    public void save(User user) {
        jpa.save(UserJpaEntity.from(user));
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "postgres")
    @Retry(name = "postgres-read")
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "postgres")
    @Retry(name = "postgres-read")
    public boolean hasOwner() {
        return jpa.existsByRole(User.Role.OWNER);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "postgres")
    public void saveFirstOwner(User user) {
        // ON CONFLICT DO NOTHING relies on the partial unique index defined in V1:
        //   CREATE UNIQUE INDEX uq_users_owner_role ON users ((role)) WHERE role = 'OWNER'
        // Any concurrent insert that races through will be silently rejected by the index,
        // and inserted == 0 surfaces that as the domain exception.
        int inserted = entityManager.createNativeQuery(
                        "INSERT INTO users (id, email, password_hash, name, role, active, created_at, updated_at) " +
                        "VALUES (:id, :email, :passwordHash, :name, 'OWNER', true, :createdAt, :updatedAt) " +
                        "ON CONFLICT ((role)) WHERE role = 'OWNER' DO NOTHING")
                .setParameter("id", user.getId().value())
                .setParameter("email", user.getEmail().value())
                .setParameter("passwordHash", user.getPasswordHash().value())
                .setParameter("name", user.getName())
                .setParameter("createdAt", user.getCreatedAt())
                .setParameter("updatedAt", user.getUpdatedAt())
                .executeUpdate();

        if (inserted == 0) {
            throw UserAlreadyExistsException.ownerAlreadyExists();
        }
    }

    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "postgres")
    @Retry(name = "postgres-read")
    public UserPage findAll(UserFilter filter, PageCriteria pageCriteria) {
        Sort sort = pageCriteria.direction() == PageCriteria.SortDirection.ASC
                ? Sort.by(pageCriteria.sortBy()).ascending()
                : Sort.by(pageCriteria.sortBy()).descending();

        var pageable = org.springframework.data.domain.PageRequest.of(
                pageCriteria.page(), pageCriteria.size(), sort);

        var page = jpa.findAll(UserSpecifications.matching(filter), pageable);

        return new UserPage(page.getContent().stream().map(UserJpaEntity::toDomain).toList(), page.getTotalElements());
    }
}
