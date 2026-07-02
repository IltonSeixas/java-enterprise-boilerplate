package com.enterprise.boilerplate.infrastructure.config;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.ChangeUserRoleUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.ListUsersUseCase;
import com.enterprise.boilerplate.application.usecase.LoginUserUseCase;
import com.enterprise.boilerplate.application.usecase.LogoutUseCase;
import com.enterprise.boilerplate.application.usecase.RefreshTokenUseCase;
import com.enterprise.boilerplate.application.usecase.RegisterUserUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.config.properties.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Use cases are plain, framework-agnostic classes; this is the only place that wires them into Spring.
@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepository userRepository,
                                                     PasswordHasherPort passwordHasher,
                                                     AuditPort audit) {
        return new RegisterUserUseCase(userRepository, passwordHasher, audit);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(UserRepository userRepository,
                                              PasswordHasherPort passwordHasher,
                                              TokenServicePort tokenService,
                                              AuditPort audit,
                                              JwtProperties jwtProperties) {
        return new LoginUserUseCase(userRepository, passwordHasher, tokenService, audit, jwtProperties.accessTokenExpiryMinutes());
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(UserRepository userRepository,
                                                     TokenServicePort tokenService,
                                                     AuditPort audit,
                                                     JwtProperties jwtProperties) {
        return new RefreshTokenUseCase(userRepository, tokenService, audit, jwtProperties.accessTokenExpiryMinutes());
    }

    @Bean
    public GetUserUseCase getUserUseCase(UserRepository userRepository) {
        return new GetUserUseCase(userRepository);
    }

    @Bean
    public UpdateProfileUseCase updateProfileUseCase(UserRepository userRepository, AuditPort audit) {
        return new UpdateProfileUseCase(userRepository, audit);
    }

    @Bean
    public ChangePasswordUseCase changePasswordUseCase(UserRepository userRepository,
                                                         PasswordHasherPort passwordHasher,
                                                         TokenServicePort tokenService,
                                                         AuditPort audit) {
        return new ChangePasswordUseCase(userRepository, passwordHasher, tokenService, audit);
    }

    @Bean
    public LogoutUseCase logoutUseCase(TokenServicePort tokenService, AuditPort audit) {
        return new LogoutUseCase(tokenService, audit);
    }

    @Bean
    public ChangeUserRoleUseCase changeUserRoleUseCase(UserRepository userRepository, AuditPort audit) {
        return new ChangeUserRoleUseCase(userRepository, audit);
    }

    @Bean
    public ListUsersUseCase listUsersUseCase(UserRepository userRepository) {
        return new ListUsersUseCase(userRepository);
    }
}
