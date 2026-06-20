package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.ChangeUserRoleUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.LoginUserUseCase;
import com.enterprise.boilerplate.application.usecase.LogoutUseCase;
import com.enterprise.boilerplate.application.usecase.RefreshTokenUseCase;
import com.enterprise.boilerplate.application.usecase.RegisterUserUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.infrastructure.persistence.memory.InMemoryUserRepository;
import com.enterprise.boilerplate.interfaces.grpc.proto.AuthResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.AuthServiceGrpc;
import com.enterprise.boilerplate.interfaces.grpc.proto.GetMeRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.LoginRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.RegisterRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.UpdateProfileRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserServiceGrpc;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.Metadata;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the gRPC layer end to end over an in-process transport, wiring the real
 * use cases against in-memory adapters — mirroring the bufconn/loopback approach used
 * by the Go and TypeScript boilerplates' gRPC integration suites.
 */
@Tag("integration")
class GrpcServerIntegrationTest {

    private static final String SERVER_NAME = "grpc-integration-" + UUID.randomUUID();

    private Server server;
    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub authStub;
    private UserServiceGrpc.UserServiceBlockingStub userStub;
    private FakeTokenService tokenService;

    @BeforeEach
    void startServer() throws Exception {
        UserRepository userRepository = new InMemoryUserRepository();
        PasswordHasherPort hasher = new PlainTextHasher();
        tokenService = new FakeTokenService();
        AuditPort audit = event -> { };
        long accessTokenExpiryMinutes = 15;

        var registerUser = new RegisterUserUseCase(userRepository, hasher, audit);
        var loginUser = new LoginUserUseCase(userRepository, hasher, tokenService, audit, accessTokenExpiryMinutes);
        var refreshToken = new RefreshTokenUseCase(userRepository, tokenService, audit, accessTokenExpiryMinutes);
        var logoutUser = new LogoutUseCase(tokenService, audit);
        var getUser = new GetUserUseCase(userRepository);
        var updateProfile = new UpdateProfileUseCase(userRepository);
        var changePassword = new ChangePasswordUseCase(userRepository, hasher, tokenService, audit);
        var changeUserRole = new ChangeUserRoleUseCase(userRepository, audit);

        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(new AuthGrpcService(registerUser, loginUser, refreshToken, logoutUser))
                .addService(new UserGrpcService(getUser, updateProfile, changePassword, changeUserRole))
                .intercept(new GrpcAuthenticationInterceptor(tokenService, userRepository))
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        authStub = AuthServiceGrpc.newBlockingStub(channel);
        userStub = UserServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void rejectsUserServiceCallsWithoutBearerToken() {
        assertThatThrownBy(() -> userStub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void registersLogsInAndReturnsAuthenticatedProfile() {
        UserResponse registered = authStub.register(RegisterRequest.newBuilder()
                .setEmail("integration@example.com")
                .setPassword("super-secret-password")
                .setName("Integration Tester")
                .build());
        assertThat(registered.getEmail()).isEqualTo("integration@example.com");

        AuthResponse session = authStub.login(LoginRequest.newBuilder()
                .setEmail("integration@example.com")
                .setPassword("super-secret-password")
                .build());
        assertThat(session.getAccessToken()).isNotBlank();
        assertThat(session.getRefreshToken()).isNotBlank();

        UserResponse me = authenticatedUserStub(session.getAccessToken())
                .getMe(GetMeRequest.getDefaultInstance());
        assertThat(me.getId()).isEqualTo(registered.getId());
        assertThat(me.getName()).isEqualTo("Integration Tester");
    }

    @Test
    void updatesProfileNameThroughUserService() {
        authStub.register(RegisterRequest.newBuilder()
                .setEmail("profile@example.com")
                .setPassword("super-secret-password")
                .setName("Before Update")
                .build());
        AuthResponse session = authStub.login(LoginRequest.newBuilder()
                .setEmail("profile@example.com")
                .setPassword("super-secret-password")
                .build());

        UserResponse updated = authenticatedUserStub(session.getAccessToken())
                .updateProfile(UpdateProfileRequest.newBuilder().setName("After Update").build());
        assertThat(updated.getName()).isEqualTo("After Update");
    }

    @Test
    void rejectsRegistrationWithDuplicateEmail() {
        authStub.register(RegisterRequest.newBuilder()
                .setEmail("duplicate@example.com")
                .setPassword("super-secret-password")
                .setName("First")
                .build());

        assertThatThrownBy(() -> authStub.register(RegisterRequest.newBuilder()
                .setEmail("duplicate@example.com")
                .setPassword("super-secret-password")
                .setName("Second")
                .build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("ALREADY_EXISTS");
    }

    private UserServiceGrpc.UserServiceBlockingStub authenticatedUserStub(String accessToken) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + accessToken);
        return userStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private static final class PlainTextHasher implements PasswordHasherPort {
        @Override
        public PasswordHash hash(String rawPassword) {
            return PasswordHash.of("hashed:" + rawPassword);
        }

        @Override
        public boolean verify(String rawPassword, PasswordHash hash) {
            return hash.value().equals("hashed:" + rawPassword);
        }
    }

    private static final class FakeTokenService implements TokenServicePort {
        private final Map<String, String> accessTokens = new ConcurrentHashMap<>();
        private final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

        @Override
        public String issueAccessToken(User user) {
            String token = "access-" + UUID.randomUUID();
            accessTokens.put(token, user.getId().toString());
            return token;
        }

        @Override
        public String issueRefreshToken(User user) {
            String token = "refresh-" + UUID.randomUUID();
            refreshTokens.put(token, user.getId().toString());
            return token;
        }

        @Override
        public Optional<String> validateAccessToken(String token) {
            return Optional.ofNullable(accessTokens.get(token));
        }

        @Override
        public Optional<String> resolveUserIdFromRefreshToken(String refreshToken) {
            return Optional.ofNullable(refreshTokens.get(refreshToken));
        }

        @Override
        public void revokeRefreshToken(String refreshToken) {
            refreshTokens.remove(refreshToken);
        }

        @Override
        public void revokeAllRefreshTokens(String userId) {
            refreshTokens.values().removeIf(userId::equals);
        }
    }
}
