package com.enterprise.boilerplate.infrastructure.persistence;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
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

    @Test
    void save_withDuplicateEmail_throwsUserAlreadyExistsException() {
        User first = User.create(Email.of("dup@example.com"), HASH, "First", User.Role.USER);
        User second = User.create(Email.of("dup@example.com"), HASH, "Second", User.Role.USER);
        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void save_updatingExistingUser_doesNotThrow() {
        User user = User.create(Email.of("update@example.com"), HASH, "Before", User.Role.USER);
        repository.save(user);
        user.updateProfile("After");

        repository.save(user);

        assertThat(repository.findById(user.getId()).map(User::getName)).contains("After");
    }

    @Test
    void findAll_withRoleFilter_returnsOnlyMatchingUsers() {
        repository.save(User.create(Email.of("admin@example.com"), HASH, "Admin", User.Role.ADMIN));
        repository.save(User.create(Email.of("user@example.com"), HASH, "Regular", User.Role.USER));

        var page = repository.findAll(new UserFilter(User.Role.ADMIN, null, null), PageCriteria.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void findAll_withNameFilter_isCaseInsensitiveSubstringMatch() {
        repository.save(User.create(Email.of("alice@example.com"), HASH, "Alice Wonderland", User.Role.USER));
        repository.save(User.create(Email.of("bob@example.com"), HASH, "Bob Builder", User.Role.USER));

        var page = repository.findAll(new UserFilter(null, null, "ALICE"), PageCriteria.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getName()).isEqualTo("Alice Wonderland");
    }

    @Test
    void findAll_withActiveFilter_returnsOnlyActiveOrInactiveAsRequested() {
        User active = User.create(Email.of("active@example.com"), HASH, "Active", User.Role.USER);
        User inactive = User.create(Email.of("inactive@example.com"), HASH, "Inactive", User.Role.USER);
        inactive.deactivate();
        repository.save(active);
        repository.save(inactive);

        var page = repository.findAll(new UserFilter(null, false, null), PageCriteria.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getName()).isEqualTo("Inactive");
    }

    @Test
    void findAll_pagesResultsAccordingToPageCriteria() {
        for (int i = 0; i < 5; i++) {
            repository.save(User.create(Email.of("user" + i + "@example.com"), HASH, "User" + i, User.Role.USER));
        }

        var firstPage = repository.findAll(UserFilter.all(), PageCriteria.of(0, 2));
        var secondPage = repository.findAll(UserFilter.all(), PageCriteria.of(1, 2));
        var thirdPage = repository.findAll(UserFilter.all(), PageCriteria.of(2, 2));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(secondPage.content()).hasSize(2);
        assertThat(thirdPage.content()).hasSize(1);
        assertThat(firstPage.totalElements()).isEqualTo(5);
    }

    @Test
    void findAll_withPageBeyondResults_returnsEmptyContent() {
        repository.save(User.create(Email.of("solo@example.com"), HASH, "Solo", User.Role.USER));

        var page = repository.findAll(UserFilter.all(), PageCriteria.of(5, 10));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
