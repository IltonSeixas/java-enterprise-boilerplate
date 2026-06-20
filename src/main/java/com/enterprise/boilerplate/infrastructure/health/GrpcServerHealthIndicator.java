package com.enterprise.boilerplate.infrastructure.health;

import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.HealthStatusManager;
import net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class GrpcServerHealthIndicator implements HealthIndicator {

    private final GrpcServerLifecycle grpcServerLifecycle;
    private final HealthStatusManager grpcHealthStatusManager;

    public GrpcServerHealthIndicator(GrpcServerLifecycle grpcServerLifecycle,
                                      HealthStatusManager grpcHealthStatusManager) {
        this.grpcServerLifecycle = grpcServerLifecycle;
        this.grpcHealthStatusManager = grpcHealthStatusManager;
    }

    @Override
    public Health health() {
        boolean running = grpcServerLifecycle.isRunning();
        grpcHealthStatusManager.setStatus(
                HealthStatusManager.SERVICE_NAME_ALL_SERVICES,
                running ? ServingStatus.SERVING : ServingStatus.NOT_SERVING);
        return running ? Health.up().build() : Health.down().build();
    }
}
