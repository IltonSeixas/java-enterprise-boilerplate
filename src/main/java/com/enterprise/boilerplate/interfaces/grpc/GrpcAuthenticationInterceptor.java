package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import java.util.Set;

/**
 * Validates the {@code authorization: Bearer <token>} request metadata for protected
 * methods and exposes the resulting caller via {@link #CALLER_CONTEXT_KEY}, mirroring
 * the active-account check performed by the REST {@code JwtAuthenticationFilter}.
 *
 * <p>Public methods (no token required) are listed in {@link #PUBLIC_METHODS}. All other
 * methods must supply a valid Bearer access token.
 */
@GrpcGlobalServerInterceptor
@Order(0)
public class GrpcAuthenticationInterceptor implements ServerInterceptor {

    public static final Context.Key<GrpcAuthenticatedCaller> CALLER_CONTEXT_KEY = Context.key("grpcAuthenticatedCaller");

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER_PREFIX = "Bearer ";

    // Full method names in gRPC format: "<package>.<Service>/<Method>"
    private static final Set<String> PUBLIC_METHODS = Set.of(
            "boilerplate.v1.AuthService/Register",
            "boilerplate.v1.AuthService/Login",
            "boilerplate.v1.AuthService/RefreshToken"
    );

    private final TokenServicePort tokenService;
    private final UserRepository userRepository;

    public GrpcAuthenticationInterceptor(TokenServicePort tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                  Metadata headers,
                                                                  ServerCallHandler<ReqT, RespT> next) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        if (PUBLIC_METHODS.contains(fullMethodName)) {
            return next.startCall(call, headers);
        }

        String header = headers.get(AUTHORIZATION_KEY);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            call.close(Status.UNAUTHENTICATED.withDescription("missing bearer token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String token = header.substring(BEARER_PREFIX.length());
        var userId = tokenService.validateAccessToken(token).orElse(null);
        if (userId == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("invalid or expired token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        var user = userRepository.findById(UserId.of(userId)).orElse(null);
        if (user == null || !user.isActive()) {
            call.close(Status.UNAUTHENTICATED.withDescription("invalid or expired token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        var caller = new GrpcAuthenticatedCaller(userId, user.getRole().name());
        Context context = Context.current().withValue(CALLER_CONTEXT_KEY, caller);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
