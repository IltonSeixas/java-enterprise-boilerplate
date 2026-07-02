package com.enterprise.boilerplate.interfaces.grpc;

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
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import com.enterprise.boilerplate.interfaces.grpc.proto.GetMeRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.LoginRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.AuthServiceGrpc;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcAuthenticationInterceptorTest {

    @Mock private TokenServicePort tokenService;
    @Mock private UserRepository userRepository;
    @Mock private RegisterUserUseCase registerUser;
    @Mock private LoginUserUseCase loginUser;
    @Mock private RefreshTokenUseCase refreshToken;
    @Mock private LogoutUseCase logoutUser;
    @Mock private GetUserUseCase getUser;
    @Mock private UpdateProfileUseCase updateProfile;
    @Mock private ChangePasswordUseCase changePassword;
    @Mock private ChangeUserRoleUseCase changeUserRole;

    private Server server;
    private ManagedChannel channel;

    private static final String SERVER_NAME = "grpc-auth-interceptor-test";
    private static final PasswordHash HASH =
            PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @BeforeEach
    void setUp() throws Exception {
        var interceptor = new GrpcAuthenticationInterceptor(tokenService, userRepository);

        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(new AuthGrpcService(registerUser, loginUser, refreshToken, logoutUser))
                .addService(new UserGrpcService(getUser, updateProfile, changePassword, changeUserRole))
                .intercept(interceptor)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static Metadata bearerHeader(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return headers;
    }

    @Test
    void allowsPublicMethods_withoutAnyToken() {
        // Login is public — interceptor passes it through without a token.
        // The use case mock returns null by default → NullPointerException → INTERNAL.
        // The key assertion is that it is NOT UNAUTHENTICATED.
        var stub = AuthServiceGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> stub.login(LoginRequest.newBuilder()
                .setEmail("a@b.com").setPassword("pass").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageNotContaining("UNAUTHENTICATED");
    }

    @Test
    void blocksProtectedMethods_withoutToken() {
        var stub = UserServiceGrpc.newBlockingStub(channel);

        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void blocksProtectedMethods_withMalformedAuthHeader() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Token abc");
        var stub = UserServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void blocksProtectedMethods_whenTokenValidationFails() {
        when(tokenService.validateAccessToken("bad-token")).thenReturn(Optional.empty());

        var stub = UserServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(bearerHeader("bad-token")));

        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void blocksProtectedMethods_whenUserNotFound() {
        String userId = UUID.randomUUID().toString();
        when(tokenService.validateAccessToken("valid-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.empty());

        var stub = UserServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(bearerHeader("valid-token")));

        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void blocksProtectedMethods_whenUserIsInactive() {
        String userId = UUID.randomUUID().toString();
        User inactiveUser = User.create(Email.of("a@b.com"), HASH, "Alice", User.Role.USER);
        inactiveUser.deactivate();

        when(tokenService.validateAccessToken("valid-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(UserId.of(userId))).thenReturn(Optional.of(inactiveUser));

        var stub = UserServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(bearerHeader("valid-token")));

        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAUTHENTICATED");
    }

    @Test
    void allowsProtectedMethods_whenTokenAndUserAreValid() {
        String userId = UUID.randomUUID().toString();
        User activeUser = User.create(Email.of("a@b.com"), HASH, "Alice", User.Role.USER);

        when(tokenService.validateAccessToken("valid-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(any())).thenReturn(Optional.of(activeUser));

        var stub = UserServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(bearerHeader("valid-token")));

        // getUser mock returns null → NPE in service → INTERNAL, not UNAUTHENTICATED
        assertThatThrownBy(() -> stub.getMe(GetMeRequest.getDefaultInstance()))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageNotContaining("UNAUTHENTICATED");
    }
}
