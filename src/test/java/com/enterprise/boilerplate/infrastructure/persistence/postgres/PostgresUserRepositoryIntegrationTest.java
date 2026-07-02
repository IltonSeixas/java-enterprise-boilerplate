package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres")
class PostgresUserRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("boilerplate")
            .withUsername("boilerplate")
            .withPassword("boilerplate");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.execute("DELETE FROM users");
    }

    private static User newOwnerCandidate(String email) {
        return User.create(
                Email.of(email),
                PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"),
                "Test User",
                User.Role.OWNER);
    }

    @Test
    void saveFirstOwner_whenNoOwnerExists_persistsUserAsOwner() {
        User user = newOwnerCandidate("first-owner@example.com");

        userRepository.saveFirstOwner(user);

        assertThat(userRepository.hasOwner()).isTrue();
        assertThat(userRepository.findById(user.getId()))
                .isPresent()
                .get()
                .extracting(User::getRole)
                .isEqualTo(User.Role.OWNER);
    }

    @Test
    void saveFirstOwner_whenOwnerAlreadyExists_throwsAndDoesNotPersistUser() {
        userRepository.saveFirstOwner(newOwnerCandidate("existing-owner@example.com"));

        User racingUser = newOwnerCandidate("racing-owner@example.com");

        assertThatThrownBy(() -> userRepository.saveFirstOwner(racingUser))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(userRepository.findById(racingUser.getId())).isEmpty();
    }

    @Test
    void findAll_withRoleFilter_returnsOnlyMatchingUsersAndCorrectTotal() {
        userRepository.save(User.create(Email.of("admin1@example.com"),
                PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"), "Admin One", User.Role.ADMIN));
        userRepository.save(User.create(Email.of("user1@example.com"),
                PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"), "User One", User.Role.USER));

        var page = userRepository.findAll(new UserFilter(User.Role.ADMIN, null, null), PageCriteria.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void findAll_withPagination_returnsRequestedSlice() {
        for (int i = 0; i < 5; i++) {
            userRepository.save(User.create(Email.of("page-user" + i + "@example.com"),
                    PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"), "PageUser" + i, User.Role.USER));
        }

        var firstPage = userRepository.findAll(UserFilter.all(), PageCriteria.of(0, 2));
        var secondPage = userRepository.findAll(UserFilter.all(), PageCriteria.of(1, 2));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(secondPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
    }
}
