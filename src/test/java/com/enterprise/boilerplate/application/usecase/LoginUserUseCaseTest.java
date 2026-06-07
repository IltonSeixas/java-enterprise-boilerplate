package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.LoginRequest;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");
    private static final long ACCESS_TOKEN_EXPIRY_MINUTES = 15L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenServicePort tokenService;

    private LoginUserUseCase newUseCase() {
        return new LoginUserUseCase(userRepository, passwordHasher, tokenService, ACCESS_TOKEN_EXPIRY_MINUTES);
    }

    private User activeUser() {
        return User.create(EMAIL, HASH, "Alice", User.Role.USER);
    }

    @Test
    void execute_withUnknownEmail_throwsUserNotFoundException() {
        var useCase = newUseCase();
        var request = new LoginRequest(EMAIL.value(), "strongpassword1");
        when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_withInactiveAccount_throwsInactiveUserException() {
        var useCase = newUseCase();
        var request = new LoginRequest(EMAIL.value(), "strongpassword1");
        User user = activeUser();
        user.deactivate();
        when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InactiveUserException.class);
    }

    @Test
    void execute_withWrongPassword_throwsInvalidPasswordException() {
        var useCase = newUseCase();
        var request = new LoginRequest(EMAIL.value(), "wrongpassword");
        when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.verify(request.password(), HASH)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void execute_withValidCredentials_returnsAuthResponseWithTokens() {
        var useCase = newUseCase();
        var request = new LoginRequest(EMAIL.value(), "strongpassword1");
        when(userRepository.findByEmail(any(Email.class))).thenReturn(Optional.of(activeUser()));
        when(passwordHasher.verify(request.password(), HASH)).thenReturn(true);
        when(tokenService.issueAccessToken(any(User.class))).thenReturn("access-token");
        when(tokenService.issueRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = useCase.execute(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(ACCESS_TOKEN_EXPIRY_MINUTES * 60);
    }
}
