package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private TokenServicePort tokenService;

    @Test
    void execute_revokesGivenRefreshToken() {
        var useCase = new LogoutUseCase(tokenService);

        useCase.execute("refresh-token");

        verify(tokenService).revokeRefreshToken("refresh-token");
    }
}
