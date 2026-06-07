package com.enterprise.boilerplate.interfaces.grpc;

/**
 * Identity extracted from the access token by {@link GrpcAuthenticationInterceptor},
 * mirroring the principal that {@code JwtAuthenticationFilter} attaches to REST requests.
 */
public record GrpcAuthenticatedCaller(String userId, String role) {
}
