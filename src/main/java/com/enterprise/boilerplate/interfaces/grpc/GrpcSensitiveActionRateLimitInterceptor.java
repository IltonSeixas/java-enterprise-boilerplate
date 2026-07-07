package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.application.ratelimit.SlidingWindowRateLimiter;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import java.time.Duration;
import java.util.Set;

/**
 * Per-user rate limiter for sensitive authenticated account actions over gRPC:
 * password change and role change, mirroring the REST edge's
 * {@code SensitiveActionRateLimitFilter}. Both require a valid access token, so this
 * is not a brute-force vector in the same sense as the public auth methods — but a
 * holder of a (possibly stolen) access token could otherwise hammer these methods
 * without limit, either to brute-force the current password before the token expires,
 * or simply to force repeated expensive Argon2id verification as a cost-amplification/
 * DoS vector.
 *
 * Ordered to run after {@link GrpcAuthenticationInterceptor} (which has
 * {@code @Order(0)}) so the authenticated caller is already available via
 * {@link GrpcAuthenticationInterceptor#CALLER_CONTEXT_KEY} to key the limit on.
 */
@GrpcGlobalServerInterceptor
@Order(1)
public class GrpcSensitiveActionRateLimitInterceptor implements ServerInterceptor {

    static final int MAX_REQUESTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:grpc-sensitive:";

    private static final Set<String> RATE_LIMITED_METHODS = Set.of(
            "boilerplate.v1.UserService/ChangePassword",
            "boilerplate.v1.UserService/ChangeRole"
    );

    private final SlidingWindowRateLimiter rateLimiter;

    public GrpcSensitiveActionRateLimitInterceptor(RateLimitPort rateLimitPort) {
        this.rateLimiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                  Metadata headers,
                                                                  ServerCallHandler<ReqT, RespT> next) {
        if (!RATE_LIMITED_METHODS.contains(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }

        var caller = GrpcAuthenticationInterceptor.CALLER_CONTEXT_KEY.get();
        if (caller == null) {
            // No authenticated caller to key on — GrpcAuthenticationInterceptor already
            // rejects unauthenticated calls to these methods before this point is reached
            // in practice, but fail open to that same downstream check rather than crash.
            return next.startCall(call, headers);
        }

        if (rateLimiter.isRateLimited(RATE_LIMIT_KEY_PREFIX + caller.userId())) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("rate limit exceeded"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }
}
