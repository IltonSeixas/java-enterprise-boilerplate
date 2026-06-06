package com.enterprise.boilerplate.infrastructure.persistence;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.infrastructure.persistence.memory.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryUserRepositoryTest {

    private InMemoryUserRepository repository;

    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
    }

    @Test
    void save_and_findById_returnsUser() {
        User user = User.create(Email.of("a@example.com"), HASH, "Alice", User.Role.USER);
        repository.save(user);

        Optional<User> found = repository.findById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    void findById_withUnknownId_returnsEmpty() {
        assertThat(repository.findById(com.enterprise.boilerplate.domain.valueobject.UserId.generate())).isEmpty();
    }

    @Test
    void saveFirstOwner_persistsOwner() {
        User owner = User.create(Email.of("owner@example.com"), HASH, "Owner", User.Role.OWNER);
        repository.saveFirstOwner(owner);

        assertThat(repository.hasOwner()).isTrue();
    }

    @Test
    void saveFirstOwner_secondCall_throwsUserAlreadyExistsException() {
        User owner1 = User.create(Email.of("owner1@example.com"), HASH, "Owner1", User.Role.OWNER);
        User owner2 = User.create(Email.of("owner2@example.com"), HASH, "Owner2", User.Role.OWNER);

        repository.saveFirstOwner(owner1);

        assertThatThrownBy(() -> repository.saveFirstOwner(owner2))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void saveFirstOwner_concurrentCalls_onlyOneSucceeds() throws InterruptedException {
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    User owner = User.create(
                            Email.of("owner" + index + "@example.com"), HASH, "Owner" + index, User.Role.OWNER);
                    repository.saveFirstOwner(owner);
                    successes.incrementAndGet();
                } catch (UserAlreadyExistsException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(repository.hasOwner()).isTrue();
    }
}
