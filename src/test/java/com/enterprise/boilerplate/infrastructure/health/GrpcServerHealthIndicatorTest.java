package com.enterprise.boilerplate.infrastructure.health;

import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcServerHealthIndicatorTest {

    @Mock
    private GrpcServerLifecycle grpcServerLifecycle;

    @Mock
    private HealthStatusManager grpcHealthStatusManager;

    @Test
    void health_whenServerIsRunning_returnsUpAndMarksGrpcHealthServiceServing() {
        when(grpcServerLifecycle.isRunning()).thenReturn(true);

        Status status = new GrpcServerHealthIndicator(grpcServerLifecycle, grpcHealthStatusManager)
                .health().getStatus();

        assertThat(status).isEqualTo(Status.UP);
        verify(grpcHealthStatusManager).setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.SERVING);
    }

    @Test
    void health_whenServerIsNotRunning_returnsDownAndMarksGrpcHealthServiceNotServing() {
        when(grpcServerLifecycle.isRunning()).thenReturn(false);

        Status status = new GrpcServerHealthIndicator(grpcServerLifecycle, grpcHealthStatusManager)
                .health().getStatus();

        assertThat(status).isEqualTo(Status.DOWN);
        verify(grpcHealthStatusManager).setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, ServingStatus.NOT_SERVING);
    }
}
