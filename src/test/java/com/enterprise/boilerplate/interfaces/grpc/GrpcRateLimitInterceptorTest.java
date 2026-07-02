package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcRateLimitInterceptorTest {

    @Mock private RateLimitPort rateLimitPort;
    @Mock private RateLimitProperties rateLimitProperties;
    @Mock private ServerCall<Object, Object> call;
    @Mock private ServerCallHandler<Object, Object> next;
    @Mock private ServerCall.Listener<Object> listener;
    @Mock private MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        when(rateLimitProperties.trustForwardedHeaders()).thenReturn(false);
        interceptor = new GrpcRateLimitInterceptor(rateLimitPort, rateLimitProperties);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
    }

    @Test
    void passesThrough_whenMethodIsNotRateLimited() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/GetUser");
        when(next.startCall(any(), any())).thenReturn(listener);

        Metadata headers = new Metadata();
        ServerCall.Listener<Object> result = interceptor.interceptCall(call, headers, next);

        assertThat(result).isSameAs(listener);
        verifyNoInteractions(rateLimitPort);
    }

    @Test
    void checksRateLimit_forRegister() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Register");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(rateLimitPort).increment(any(), eq(GrpcRateLimitInterceptor.WINDOW));
    }

    @Test
    void checksRateLimit_forLogin() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Login");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(rateLimitPort).increment(any(), eq(GrpcRateLimitInterceptor.WINDOW));
    }

    @Test
    void checksRateLimit_forRefreshToken() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/RefreshToken");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(rateLimitPort).increment(any(), eq(GrpcRateLimitInterceptor.WINDOW));
    }

    @Test
    void closesCall_withResourceExhausted_whenLimitExceeded() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Login");
        when(rateLimitPort.increment(any(), any(Duration.class)))
                .thenReturn((long) GrpcRateLimitInterceptor.MAX_REQUESTS + 1);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        verifyNoInteractions(next);
        assertThat(result).isNotNull();
    }

    @Test
    void allowsRequest_whenCountIsExactlyAtLimit() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Login");
        when(rateLimitPort.increment(any(), any(Duration.class)))
                .thenReturn((long) GrpcRateLimitInterceptor.MAX_REQUESTS);
        when(next.startCall(any(), any())).thenReturn(listener);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        assertThat(result).isSameAs(listener);
        verify(call, never()).close(any(), any());
    }

    @Test
    void usesFallback_whenRedisUnavailable() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Login");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(-1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        // First request in a fresh window — fallback counter starts at 1, below limit.
        assertThat(result).isSameAs(listener);
    }

    @Test
    void usesForwardedHeader_whenTrustForwardedIsEnabled() {
        when(rateLimitProperties.trustForwardedHeaders()).thenReturn(true);
        interceptor = new GrpcRateLimitInterceptor(rateLimitPort, rateLimitProperties);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.AuthService/Login");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        Metadata headers = new Metadata();
        Metadata.Key<String> forwardedKey =
                Metadata.Key.of("x-forwarded-for", Metadata.ASCII_STRING_MARSHALLER);
        headers.put(forwardedKey, "10.0.0.1, 172.16.0.1");

        interceptor.interceptCall(call, headers, next);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitPort).increment(keyCaptor.capture(), any());
        // Rightmost hop from the header should be the key suffix.
        assertThat(keyCaptor.getValue()).endsWith("172.16.0.1");
    }
}
