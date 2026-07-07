package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
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
class GrpcSensitiveActionRateLimitInterceptorTest {

    @Mock private RateLimitPort rateLimitPort;
    @Mock private ServerCall<Object, Object> call;
    @Mock private ServerCallHandler<Object, Object> next;
    @Mock private ServerCall.Listener<Object> listener;
    @Mock private MethodDescriptor<Object, Object> methodDescriptor;

    private GrpcSensitiveActionRateLimitInterceptor interceptor;
    private Context previousContext;
    private Context attachedContext;

    @BeforeEach
    void setUp() {
        interceptor = new GrpcSensitiveActionRateLimitInterceptor(rateLimitPort);
        when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
    }

    @AfterEach
    void detachContext() {
        if (attachedContext != null) {
            attachedContext.detach(previousContext);
        }
    }

    private void authenticateAs(String userId) {
        attachedContext = Context.current()
                .withValue(GrpcAuthenticationInterceptor.CALLER_CONTEXT_KEY,
                        new GrpcAuthenticatedCaller(userId, "USER"));
        previousContext = attachedContext.attach();
    }

    @Test
    void passesThrough_whenMethodIsNotRateLimited() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/GetUser");
        when(next.startCall(any(), any())).thenReturn(listener);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        assertThat(result).isSameAs(listener);
        verifyNoInteractions(rateLimitPort);
    }

    @Test
    void checksRateLimit_forChangePassword() {
        authenticateAs("user-1");
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangePassword");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(rateLimitPort).increment(any(), eq(GrpcSensitiveActionRateLimitInterceptor.WINDOW));
    }

    @Test
    void checksRateLimit_forChangeRole() {
        authenticateAs("user-1");
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangeRole");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(rateLimitPort).increment(any(), eq(GrpcSensitiveActionRateLimitInterceptor.WINDOW));
    }

    @Test
    void keysRateLimitByAuthenticatedUserId() {
        authenticateAs("user-42");
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangePassword");
        when(rateLimitPort.increment(any(), any(Duration.class))).thenReturn(1L);
        when(next.startCall(any(), any())).thenReturn(listener);

        interceptor.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitPort).increment(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).endsWith("user-42");
    }

    @Test
    void closesCall_withResourceExhausted_whenLimitExceeded() {
        authenticateAs("user-1");
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangePassword");
        when(rateLimitPort.increment(any(), any(Duration.class)))
                .thenReturn((long) GrpcSensitiveActionRateLimitInterceptor.MAX_REQUESTS + 1);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        verifyNoInteractions(next);
        assertThat(result).isNotNull();
    }

    @Test
    void allowsRequest_whenCountIsExactlyAtLimit() {
        authenticateAs("user-1");
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangePassword");
        when(rateLimitPort.increment(any(), any(Duration.class)))
                .thenReturn((long) GrpcSensitiveActionRateLimitInterceptor.MAX_REQUESTS);
        when(next.startCall(any(), any())).thenReturn(listener);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        assertThat(result).isSameAs(listener);
        verify(call, never()).close(any(), any());
    }

    @Test
    void passesThrough_whenNoAuthenticatedCallerInContext() {
        when(methodDescriptor.getFullMethodName()).thenReturn("boilerplate.v1.UserService/ChangePassword");
        when(next.startCall(any(), any())).thenReturn(listener);

        ServerCall.Listener<Object> result = interceptor.interceptCall(call, new Metadata(), next);

        assertThat(result).isSameAs(listener);
        verifyNoInteractions(rateLimitPort);
    }
}
