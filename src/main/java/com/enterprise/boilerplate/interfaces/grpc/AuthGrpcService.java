package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.usecase.LoginUserUseCase;
import com.enterprise.boilerplate.application.usecase.LogoutUseCase;
import com.enterprise.boilerplate.application.usecase.RefreshTokenUseCase;
import com.enterprise.boilerplate.application.usecase.RegisterUserUseCase;
import com.enterprise.boilerplate.interfaces.grpc.proto.AuthResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.AuthServiceGrpc;
import com.enterprise.boilerplate.interfaces.grpc.proto.LoginRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.LogoutRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.LogoutResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.RefreshTokenRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.RegisterRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final RegisterUserUseCase registerUser;
    private final LoginUserUseCase loginUser;
    private final RefreshTokenUseCase refreshToken;
    private final LogoutUseCase logoutUser;

    public AuthGrpcService(RegisterUserUseCase registerUser,
                           LoginUserUseCase loginUser,
                           RefreshTokenUseCase refreshToken,
                           LogoutUseCase logoutUser) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.refreshToken = refreshToken;
        this.logoutUser = logoutUser;
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<UserResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            var response = registerUser.execute(new com.enterprise.boilerplate.application.dto.RegisterUserRequest(
                    request.getEmail(), request.getPassword(), request.getName()));
            return GrpcMappers.toProtoUserResponse(response);
        });
    }

    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            var response = loginUser.execute(new com.enterprise.boilerplate.application.dto.LoginRequest(
                    request.getEmail(), request.getPassword()));
            return GrpcMappers.toProtoAuthResponse(response);
        });
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            var response = refreshToken.execute(new com.enterprise.boilerplate.application.dto.RefreshTokenRequest(
                    request.getRefreshToken()));
            return GrpcMappers.toProtoAuthResponse(response);
        });
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            logoutUser.execute(request.getRefreshToken());
            return LogoutResponse.getDefaultInstance();
        });
    }
}
