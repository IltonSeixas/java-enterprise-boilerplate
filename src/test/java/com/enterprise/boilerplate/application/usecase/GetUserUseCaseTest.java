package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserUseCaseTest {

    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Mock
    private UserRepository userRepository;

    private GetUserUseCase newUseCase() {
        return new GetUserUseCase(userRepository);
    }

    private User userWithRole(User.Role role) {
        return User.create(Email.of("user-" + role.name().toLowerCase() + "@example.com"), HASH, "Alice", role);
    }

    @Test
    void execute_whenCallerNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        UserId callerId = UserId.generate();
        UserId targetId = UserId.generate();
        when(userRepository.findById(callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(targetId.toString(), callerId.toString()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_whenTargetNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        User caller = userWithRole(User.Role.USER);
        UserId targetId = UserId.generate();
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(targetId.toString(), caller.getId().toString()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_whenMemberRequestsAnotherUser_throwsForbiddenException() {
        var useCase = newUseCase();
        User caller = userWithRole(User.Role.USER);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> useCase.execute(target.getId().toString(), caller.getId().toString()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void execute_whenMemberRequestsSelf_returnsOwnProfile() {
        var useCase = newUseCase();
        User caller = userWithRole(User.Role.USER);
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        UserResponse response = useCase.execute(caller.getId().toString(), caller.getId().toString());

        assertThat(response.id()).isEqualTo(caller.getId().toString());
    }

    @Test
    void execute_whenAdminRequestsAnotherUser_returnsTargetProfile() {
        var useCase = newUseCase();
        User admin = userWithRole(User.Role.ADMIN);
        User target = userWithRole(User.Role.USER);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        UserResponse response = useCase.execute(target.getId().toString(), admin.getId().toString());

        assertThat(response.id()).isEqualTo(target.getId().toString());
    }
}
