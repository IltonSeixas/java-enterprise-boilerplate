package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.RegisterUserRequest;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    private RegisterUserUseCase useCase;

    private RegisterUserUseCase newUseCase() {
        return new RegisterUserUseCase(userRepository, passwordHasher);
    }

    @Test
    void execute_withExistingEmail_throwsUserAlreadyExistsException() {
        useCase = newUseCase();
        var request = new RegisterUserRequest("user@example.com", "strongpassword1", "Alice");
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveFirstOwner(any());
    }

    @Test
    void execute_whenNoOwnerExists_createsFirstUserAsOwner() {
        useCase = newUseCase();
        var request = new RegisterUserRequest("owner@example.com", "strongpassword1", "Owner");
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.hasOwner()).thenReturn(false);
        when(passwordHasher.hash(request.password())).thenReturn(HASH);

        UserResponse response = useCase.execute(request);

        assertThat(response.role()).isEqualTo(User.Role.OWNER.name());
        verify(userRepository).saveFirstOwner(any(User.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_whenOwnerAlreadyExists_createsUserWithMemberRole() {
        useCase = newUseCase();
        var request = new RegisterUserRequest("member@example.com", "strongpassword1", "Member");
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.hasOwner()).thenReturn(true);
        when(passwordHasher.hash(request.password())).thenReturn(HASH);

        UserResponse response = useCase.execute(request);

        assertThat(response.role()).isEqualTo(User.Role.USER.name());
        verify(userRepository).save(any(User.class));
        verify(userRepository, never()).saveFirstOwner(any());
    }
}
