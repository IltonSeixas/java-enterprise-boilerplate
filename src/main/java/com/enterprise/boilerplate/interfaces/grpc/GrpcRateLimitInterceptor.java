package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
import com.enterprise.boilerplate.interfaces.ratelimit.SlidingWindowRateLimiter;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.core.annotation.Order;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Set;

/**
 * Per-IP rate limiter for gRPC authentication methods, sharing its counting logic
 * with {@code AuthRateLimitFilter} on the REST layer via {@link SlidingWindowRateLimiter}.
 *
 * Only the public auth methods (Register, Login, RefreshToken) are throttled — protecting
 * against credential stuffing and token enumeration via the gRPC surface. Authenticated
 * methods are excluded: they already require a valid access token, so brute-force is not
 * a meaningful threat there.
 *
 * The client IP is read from the {@code x-forwarded-for} metadata key only when
 * {@code app.rate-limit.trust-forwarded-headers} is true (i.e. traffic arrives through a
 * trusted reverse proxy that appends its own hop). Otherwise the real peer address is
 * read from the gRPC transport attributes ({@link Grpc#TRANSPORT_ATTR_REMOTE_ADDR}),
 * which the client cannot spoof — unlike any metadata header.
 */
@GrpcGlobalServerInterceptor
@Order(-1)
public class GrpcRateLimitInterceptor implements ServerInterceptor {

    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:grpc-auth:";

    private static final Metadata.Key<String> FORWARDED_FOR_KEY =
            Metadata.Key.of("x-forwarded-for", Metadata.ASCII_STRING_MARSHALLER);

    // Full gRPC method names that are rate-limited (public auth surface).
    private static final Set<String> RATE_LIMITED_METHODS = Set.of(
            "boilerplate.v1.AuthService/Register",
            "boilerplate.v1.AuthService/Login",
            "boilerplate.v1.AuthService/RefreshToken"
    );

    private final boolean trustForwardedHeaders;
    private final SlidingWindowRateLimiter rateLimiter;

    public GrpcRateLimitInterceptor(RateLimitPort rateLimitPort, RateLimitProperties rateLimitProperties) {
        this.trustForwardedHeaders = rateLimitProperties.trustForwardedHeaders();
        this.rateLimiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                  Metadata headers,
                                                                  ServerCallHandler<ReqT, RespT> next) {
        if (!RATE_LIMITED_METHODS.contains(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }

        String ip = resolveClientIp(call, headers);
        if (rateLimiter.isRateLimited(RATE_LIMIT_KEY_PREFIX + ip)) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("rate limit exceeded"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }

    private String resolveClientIp(ServerCall<?, ?> call, Metadata headers) {
        if (trustForwardedHeaders) {
            String forwarded = headers.get(FORWARDED_FOR_KEY);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (remoteAddr instanceof InetSocketAddress inetAddr) {
            return inetAddr.getAddress().getHostAddress();
        }
        return remoteAddr != null ? remoteAddr.toString() : "unknown";
    }
}
