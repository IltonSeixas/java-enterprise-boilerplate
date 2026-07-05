package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ChangePasswordRequest;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordUseCaseTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PasswordHash CURRENT_HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$b2xk");
    private static final PasswordHash NEW_HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$bmV3");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenServicePort tokenService;

    @Mock
    private AuditPort audit;

    private ChangePasswordUseCase newUseCase() {
        return new ChangePasswordUseCase(userRepository, passwordHasher, tokenService, audit);
    }

    private User existingUser() {
        return User.create(EMAIL, CURRENT_HASH, "Alice", User.Role.USER);
    }

    @Test
    void execute_whenUserNotFound_throwsUserNotFoundException() {
        var useCase = newUseCase();
        UserId id = UserId.generate();
        var request = new ChangePasswordRequest("currentpassword", "newpassword1");
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id.toString(), request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_withWrongCurrentPassword_throwsInvalidPasswordException() {
        var useCase = newUseCase();
        User user = existingUser();
        var request = new ChangePasswordRequest("wrongpassword", "newpassword1");
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), CURRENT_HASH)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(user.id().toString(), request))
                .isInstanceOf(InvalidPasswordException.class);

        verify(userRepository, never()).save(user);
        verify(tokenService, never()).revokeAllRefreshTokens(anyString());
        verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_withValidCurrentPassword_updatesHashRevokesAllSessionsAndRecordsAuditEvent() {
        var useCase = newUseCase();
        User user = existingUser();
        String userId = user.id().toString();
        var request = new ChangePasswordRequest("currentpassword", "newpassword1");
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(passwordHasher.verify(request.currentPassword(), CURRENT_HASH)).thenReturn(true);
        when(passwordHasher.hash(request.newPassword())).thenReturn(NEW_HASH);

        useCase.execute(userId, request);

        assertThat(user.passwordHash()).isEqualTo(NEW_HASH);
        verify(userRepository).save(user);
        verify(tokenService).revokeAllRefreshTokens(userId);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.PASSWORD_CHANGED);
        assertThat(captor.getValue().actorUserId()).isEqualTo(userId);
    }
}
