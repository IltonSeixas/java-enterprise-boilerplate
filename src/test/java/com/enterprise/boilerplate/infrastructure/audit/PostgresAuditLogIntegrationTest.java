package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres")
class PostgresAuditLogIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("boilerplate")
            .withUsername("boilerplate")
            .withPassword("boilerplate");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuditPort auditLog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAuditLog() {
        jdbcTemplate.execute("DELETE FROM audit_log");
    }

    @Test
    void record_persistsEventWithAllFields() {
        AuditEvent event = AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "actor-1", "detail");

        auditLog.record(event);

        var rows = jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE id = ?", event.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("event_type")).isEqualTo("LOGIN_SUCCEEDED");
        assertThat(rows.get(0).get("actor_user_id")).isEqualTo("actor-1");
        assertThat(rows.get(0).get("target_user_id")).isEqualTo("actor-1");
        assertThat(rows.get(0).get("detail")).isEqualTo("detail");
    }

    @Test
    void record_withNullDetail_persistsNullDetail() {
        AuditEvent event = AuditEvent.of(AuditEventType.LOGOUT, "actor-2", null);

        auditLog.record(event);

        var rows = jdbcTemplate.queryForList("SELECT detail FROM audit_log WHERE id = ?", event.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("detail")).isNull();
    }
}
