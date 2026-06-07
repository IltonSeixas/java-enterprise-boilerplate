package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.UserResponse;

/**
 * Converts application DTOs into their generated protobuf message counterparts.
 */
final class GrpcMappers {

    private GrpcMappers() {
    }

    static com.enterprise.boilerplate.interfaces.grpc.proto.UserResponse toProtoUserResponse(UserResponse response) {
        return com.enterprise.boilerplate.interfaces.grpc.proto.UserResponse.newBuilder()
                .setId(response.id())
                .setEmail(response.email())
                .setName(response.name())
                .setRole(response.role())
                .setActive(response.active())
                .setCreatedAt(response.createdAt().toString())
                .build();
    }

    static com.enterprise.boilerplate.interfaces.grpc.proto.AuthResponse toProtoAuthResponse(AuthResponse response) {
        return com.enterprise.boilerplate.interfaces.grpc.proto.AuthResponse.newBuilder()
                .setAccessToken(response.accessToken())
                .setRefreshToken(response.refreshToken())
                .setTokenType(response.tokenType())
                .setExpiresIn(response.expiresIn())
                .build();
    }
}
