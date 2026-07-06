package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.RefreshTokenRequest;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");
    private static final long ACCESS_TOKEN_EXPIRY_MINUTES = 15L;
    private static final String REFRESH_TOKEN = "old-refresh-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenServicePort tokenService;

    @Mock
    private AuditPort audit;

    private RefreshTokenUseCase newUseCase() {
        return new RefreshTokenUseCase(userRepository, tokenService, audit, ACCESS_TOKEN_EXPIRY_MINUTES);
    }

    private User activeUser() {
        return User.create(EMAIL, HASH, "Alice", User.Role.USER);
    }

    @Test
    void execute_withInvalidRefreshToken_throwsInvalidTokenException() {
        var useCase = newUseCase();
        when(tokenService.resolveUserIdFromRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void execute_whenUserNoLongerExists_throwsUserNotFoundException() {
        var useCase = newUseCase();
        User user = activeUser();
        String userId = user.id().toString();
        when(tokenService.resolveUserIdFromRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(REFRESH_TOKEN)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void execute_withInactiveAccount_revokesTokenAndThrowsInactiveUserException() {
        var useCase = newUseCase();
        User user = activeUser();
        user.deactivate();
        String userId = user.id().toString();
        when(tokenService.resolveUserIdFromRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(REFRESH_TOKEN)))
                .isInstanceOf(InactiveUserException.class);

        verify(tokenService).revokeRefreshToken(REFRESH_TOKEN);
        verify(tokenService, never()).issueAccessToken(any());
        verify(audit, never()).record(any());
    }

    @Test
    void execute_withValidToken_rotatesTokenPairAndRecordsAuditEvent() {
        var useCase = newUseCase();
        User user = activeUser();
        String userId = user.id().toString();
        when(tokenService.resolveUserIdFromRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.of(userId));
        when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user)).thenReturn("new-access-token");
        when(tokenService.issueRefreshToken(user)).thenReturn("new-refresh-token");

        AuthResponse response = useCase.execute(new RefreshTokenRequest(REFRESH_TOKEN));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(tokenService).revokeRefreshToken(REFRESH_TOKEN);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.TOKEN_REFRESHED);
        assertThat(captor.getValue().actorUserId()).isEqualTo(userId);
    }

    @Test
    void execute_withReusedToken_revokesAllTokensAndRecordsAuditEventAndThrowsInvalidTokenException() {
        var useCase = newUseCase();
        String userId = UserId.generate().toString();
        when(tokenService.checkReuse(REFRESH_TOKEN)).thenReturn(Optional.of(userId));

        assertThatThrownBy(() -> useCase.execute(new RefreshTokenRequest(REFRESH_TOKEN)))
                .isInstanceOf(InvalidTokenException.class);

        verify(tokenService).revokeAllRefreshTokens(userId);
        verify(tokenService, never()).resolveUserIdFromRefreshToken(any());
        verify(userRepository, never()).findById(any());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED);
        assertThat(captor.getValue().actorUserId()).isEqualTo(userId);
    }
}
