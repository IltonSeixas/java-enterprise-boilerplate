package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP rate limiter for gRPC authentication methods, mirroring the logic of
 * {@code AuthRateLimitFilter} on the REST layer.
 *
 * Only the public auth methods (Register, Login, RefreshToken) are throttled — protecting
 * against credential stuffing and token enumeration via the gRPC surface. Authenticated
 * methods are excluded: they already require a valid access token, so brute-force is not
 * a meaningful threat there.
 *
 * The client IP is read from the {@code x-forwarded-for} metadata key only when
 * {@code rateLimiting.trustForwardedHeaders} is true (i.e. traffic arrives through a
 * trusted reverse proxy that appends its own hop). Otherwise the real peer address is
 * read from the gRPC transport attributes ({@link Grpc#TRANSPORT_ATTR_REMOTE_ADDR}),
 * which the client cannot spoof — unlike any metadata header.
 */
@GrpcGlobalServerInterceptor
@Order(-1)
public class GrpcRateLimitInterceptor implements ServerInterceptor {

    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final long SWEEP_INTERVAL_MILLIS = WINDOW.toMillis() * 5;
    private static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final String RATE_LIMIT_KEY_PREFIX = "rl:grpc-auth:";

    private static final Metadata.Key<String> FORWARDED_FOR_KEY =
            Metadata.Key.of("x-forwarded-for", Metadata.ASCII_STRING_MARSHALLER);

    // Full gRPC method names that are rate-limited (public auth surface).
    private static final Set<String> RATE_LIMITED_METHODS = Set.of(
            "boilerplate.v1.AuthService/Register",
            "boilerplate.v1.AuthService/Login",
            "boilerplate.v1.AuthService/RefreshToken"
    );

    private final RateLimitPort rateLimitPort;
    private final boolean trustForwardedHeaders;

    private record Window(AtomicInteger count, long windowStart) {}
    private final ConcurrentHashMap<String, Window> localWindows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    public GrpcRateLimitInterceptor(RateLimitPort rateLimitPort, RateLimitProperties rateLimitProperties) {
        this.rateLimitPort = rateLimitPort;
        this.trustForwardedHeaders = rateLimitProperties.trustForwardedHeaders();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                  Metadata headers,
                                                                  ServerCallHandler<ReqT, RespT> next) {
        if (!RATE_LIMITED_METHODS.contains(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }

        String ip = resolveClientIp(call, headers);
        if (isRateLimited(ip)) {
            call.close(Status.RESOURCE_EXHAUSTED.withDescription("rate limit exceeded"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }

    private boolean isRateLimited(String ip) {
        long count = rateLimitPort.increment(RATE_LIMIT_KEY_PREFIX + ip, WINDOW);
        if (count == -1L) {
            return localIncrement(ip) > MAX_REQUESTS;
        }
        return count > MAX_REQUESTS;
    }

    private long localIncrement(String ip) {
        long now = System.currentTimeMillis();
        sweepStaleEntries(now);

        Window window = localWindows.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW.toMillis()) {
                return new Window(new AtomicInteger(0), now);
            }
            return existing;
        });

        return window.count().incrementAndGet();
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

    private void sweepStaleEntries(long now) {
        long previousSweep = lastSweep.get();
        if (now - previousSweep < SWEEP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastSweep.compareAndSet(previousSweep, now)) {
            return;
        }
        localWindows.entrySet().removeIf(e -> now - e.getValue().windowStart() >= WINDOW.toMillis());
        if (localWindows.size() > MAX_TRACKED_CLIENTS) {
            localWindows.clear();
        }
    }
}
