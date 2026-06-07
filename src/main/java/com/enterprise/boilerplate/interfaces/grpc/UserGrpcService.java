package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import com.enterprise.boilerplate.interfaces.grpc.proto.ChangePasswordRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.ChangePasswordResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.GetMeRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.GetUserRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.UpdateProfileRequest;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserResponse;
import com.enterprise.boilerplate.interfaces.grpc.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final GetUserUseCase getUser;
    private final UpdateProfileUseCase updateProfile;
    private final ChangePasswordUseCase changePassword;

    public UserGrpcService(GetUserUseCase getUser,
                           UpdateProfileUseCase updateProfile,
                           ChangePasswordUseCase changePassword) {
        this.getUser = getUser;
        this.updateProfile = updateProfile;
        this.changePassword = changePassword;
    }

    @Override
    public void getMe(GetMeRequest request, StreamObserver<UserResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            String callerId = currentCaller().userId();
            var response = getUser.execute(callerId, callerId);
            return GrpcMappers.toProtoUserResponse(response);
        });
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            var response = getUser.execute(request.getUserId(), currentCaller().userId());
            return GrpcMappers.toProtoUserResponse(response);
        });
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UserResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            String callerId = currentCaller().userId();
            var response = updateProfile.execute(callerId,
                    new com.enterprise.boilerplate.application.dto.UpdateProfileRequest(request.getName()));
            return GrpcMappers.toProtoUserResponse(response);
        });
    }

    @Override
    public void changePassword(ChangePasswordRequest request, StreamObserver<ChangePasswordResponse> responseObserver) {
        GrpcCalls.handle(responseObserver, () -> {
            String callerId = currentCaller().userId();
            changePassword.execute(callerId, new com.enterprise.boilerplate.application.dto.ChangePasswordRequest(
                    request.getCurrentPassword(), request.getNewPassword()));
            return ChangePasswordResponse.getDefaultInstance();
        });
    }

    private GrpcAuthenticatedCaller currentCaller() {
        var caller = GrpcAuthenticationInterceptor.CALLER_CONTEXT_KEY.get();
        if (caller == null) {
            throw Status.UNAUTHENTICATED.withDescription("missing authenticated caller").asRuntimeException();
        }
        return caller;
    }
}
