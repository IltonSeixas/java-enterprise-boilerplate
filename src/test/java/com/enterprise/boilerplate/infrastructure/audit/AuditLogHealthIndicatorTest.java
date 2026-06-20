package com.enterprise.boilerplate.infrastructure.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogHealthIndicatorTest {

    @Mock
    private AuditLogJpaRepository jpa;

    @Test
    void health_whenRepositoryIsReachable_returnsUp() {
        when(jpa.count()).thenReturn(42L);

        Status status = new AuditLogHealthIndicator(jpa).health().getStatus();

        assertThat(status).isEqualTo(Status.UP);
    }

    @Test
    void health_whenRepositoryThrows_returnsDown() {
        when(jpa.count()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        Status status = new AuditLogHealthIndicator(jpa).health().getStatus();

        assertThat(status).isEqualTo(Status.DOWN);
    }
}
