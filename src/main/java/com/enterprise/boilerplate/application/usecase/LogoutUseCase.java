package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import org.springframework.stereotype.Service;

@Service
public class LogoutUseCase {

    private final TokenServicePort tokenService;

    public LogoutUseCase(TokenServicePort tokenService) {
        this.tokenService = tokenService;
    }

    public void execute(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }
}
