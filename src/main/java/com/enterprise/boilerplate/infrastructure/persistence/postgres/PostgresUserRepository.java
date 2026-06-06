package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
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
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(User user) {
        jpa.save(UserJpaEntity.from(user));
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }

    @Override
    public boolean hasOwner() {
        return jpa.existsOwner();
    }

    @Override
    @Transactional
    public void saveFirstOwner(User user) {
        entityManager.createNativeQuery(
                        "INSERT INTO users (id, email, password_hash, name, role, active, created_at, updated_at) " +
                        "SELECT :id, :email, :passwordHash, :name, 'OWNER', true, :createdAt, :updatedAt " +
                        "WHERE NOT EXISTS (SELECT 1 FROM users WHERE role = 'OWNER')")
                .setParameter("id", user.getId().value())
                .setParameter("email", user.getEmail().value())
                .setParameter("passwordHash", user.getPasswordHash().value())
                .setParameter("name", user.getName())
                .setParameter("createdAt", user.getCreatedAt())
                .setParameter("updatedAt", user.getUpdatedAt())
                .executeUpdate();

        if (!jpa.existsOwner()) {
            throw new UserAlreadyExistsException("An owner already exists");
        }
    }
}
