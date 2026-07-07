package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ListUsersRequest;
import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.DomainValidationException;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.PageCriteria;
import com.enterprise.boilerplate.domain.repository.UserFilter;
import com.enterprise.boilerplate.domain.repository.UserPage;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseTest {

    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Mock
    private UserRepository userRepository;

    private ListUsersUseCase newUseCase() {
        return new ListUsersUseCase(userRepository);
    }

    private ListUsersRequest request(String role, Boolean active, String nameContains, int page, int size) {
        return new ListUsersRequest(role, active, nameContains, page, size, null, null);
    }

    private User userWithRole(User.Role role) {
        return User.create(Email.of("user-" + role.name().toLowerCase() + "@example.com"), HASH, "Alice", role);
    }

    @Test
    void execute_whenCallerNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        UserId callerId = UserId.generate();
        when(userRepository.findById(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(callerId.toString(), request(null, null, null, 0, 20)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_whenCallerIsRegularUser_throwsForbiddenException() {
        var useCase = newUseCase();
        User caller = userWithRole(User.Role.USER);
        when(userRepository.findById(caller.id())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> useCase.execute(caller.id().toString(), request(null, null, null, 0, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void execute_withInvalidRoleFilter_throwsInvalidRoleException() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> useCase.execute(admin.id().toString(), request("superuser", null, null, 0, 20)))
                .isInstanceOf(InvalidRoleException.class);
    }

    @Test
    void execute_whenCallerIsAdmin_passesFilterAndPagingToRepository() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));
        when(userRepository.findAll(any(), any())).thenReturn(new UserPage(List.of(target), 1));

        PageResponse<?> response = useCase.execute(admin.id().toString(),
                new ListUsersRequest("user", true, "ali", 1, 10, "name", "DESC"));

        ArgumentCaptor<UserFilter> filterCaptor = ArgumentCaptor.forClass(UserFilter.class);
        ArgumentCaptor<PageCriteria> pageCaptor = ArgumentCaptor.forClass(PageCriteria.class);
        verify(userRepository).findAll(filterCaptor.capture(), pageCaptor.capture());

        assertThat(filterCaptor.getValue().role()).isEqualTo(User.Role.USER);
        assertThat(filterCaptor.getValue().active()).isTrue();
        assertThat(filterCaptor.getValue().nameContains()).isEqualTo("ali");
        assertThat(pageCaptor.getValue().page()).isEqualTo(1);
        assertThat(pageCaptor.getValue().size()).isEqualTo(10);
        assertThat(pageCaptor.getValue().sortBy()).isEqualTo("name");
        assertThat(pageCaptor.getValue().direction()).isEqualTo(PageCriteria.SortDirection.DESC);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void execute_whenCallerIsOwner_isAllowed() {
        var useCase = newUseCase();
        User owner = userWithRole(User.Role.OWNER);
        when(userRepository.findById(owner.id())).thenReturn(Optional.of(owner));
        when(userRepository.findAll(any(), any())).thenReturn(new UserPage(List.of(), 0));

        PageResponse<?> response = useCase.execute(owner.id().toString(), request(null, null, null, 0, 20));

        assertThat(response.content()).isEmpty();
    }

    @Test
    void execute_withInvalidSortBy_throwsDomainValidationException() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> useCase.execute(admin.id().toString(),
                new ListUsersRequest(null, null, null, 0, 20, "passwordHash", "ASC")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Invalid sortBy");
    }

    @Test
    void execute_withInvalidDirection_throwsDomainValidationException() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> useCase.execute(admin.id().toString(),
                new ListUsersRequest(null, null, null, 0, 20, "name", "UPWARD")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Invalid direction");
    }
}
