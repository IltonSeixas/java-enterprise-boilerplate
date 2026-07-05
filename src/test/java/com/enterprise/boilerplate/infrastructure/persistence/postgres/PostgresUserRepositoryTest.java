package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgresUserRepositoryTest {

    @Mock private JpaUserRepository jpa;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private PostgresUserRepository repository;

    private static User newUser() {
        return User.reconstitute(
                UserId.generate(),
                Email.of("user@example.com"),
                PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$salt$hash"),
                "Alice",
                User.Role.OWNER,
                true,
                Instant.now(),
                Instant.now());
    }

    @BeforeEach
    void setUp() {
        repository = new PostgresUserRepository(jpa);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
    }

    @Test
    void findById_returnsMappedUser_whenPresent() {
        User user = newUser();
        UserJpaEntity entity = UserJpaEntity.from(user);
        when(jpa.findById(user.id().value())).thenReturn(Optional.of(entity));

        Optional<User> result = repository.findById(user.id());

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo(user.email());
    }

    @Test
    void findById_returnsEmpty_whenAbsent() {
        UserId id = UserId.generate();
        when(jpa.findById(id.value())).thenReturn(Optional.empty());

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void findByEmail_returnsMappedUser_whenPresent() {
        User user = newUser();
        UserJpaEntity entity = UserJpaEntity.from(user);
        when(jpa.findByEmail(user.email().value())).thenReturn(Optional.of(entity));

        Optional<User> result = repository.findByEmail(user.email());

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo(user.name());
    }

    @Test
    void save_delegatesToJpaRepository() {
        User user = newUser();

        repository.save(user);

        verify(jpa).save(argThat(entity -> entity.getEmail().equals(user.email().value())));
    }

    @Test
    void existsByEmail_delegatesToJpaRepository() {
        when(jpa.existsByEmail("user@example.com")).thenReturn(true);

        assertThat(repository.existsByEmail(Email.of("user@example.com"))).isTrue();
    }

    @Test
    void hasOwner_delegatesToJpaRepository() {
        when(jpa.existsByRole(User.Role.OWNER)).thenReturn(true);

        assertThat(repository.hasOwner()).isTrue();
    }

    @Test
    void saveFirstOwner_succeeds_whenInsertAffectsOneRow() {
        User owner = newUser();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        repository.saveFirstOwner(owner);

        verify(query).executeUpdate();
    }

    @Test
    void saveFirstOwner_throwsOwnerAlreadyExists_whenInsertAffectsNoRows() {
        User owner = newUser();
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);

        assertThatThrownBy(() -> repository.saveFirstOwner(owner))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void findAll_mapsPageContentAndTotal() {
        User user = newUser();
        UserJpaEntity entity = UserJpaEntity.from(user);
        Page<UserJpaEntity> page = new PageImpl<>(List.of(entity));
        when(jpa.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        var result = repository.findAll(UserFilter.all(), PageCriteria.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo(user.email());
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void findAll_sortsDescending_whenDirectionIsDesc() {
        Page<UserJpaEntity> emptyPage = new PageImpl<>(List.of());
        when(jpa.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        var criteria = PageCriteria.of(0, 20, "name", PageCriteria.SortDirection.DESC);

        repository.findAll(UserFilter.all(), criteria);

        verify(jpa).findAll(any(Specification.class), argThat(
                (Pageable p) -> p.getSort().getOrderFor("name") != null
                        && p.getSort().getOrderFor("name").isDescending()));
    }
}
