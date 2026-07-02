package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Driver;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class FlywayConcurrentMigrationIntegrationTest {

    private static final int CONCURRENT_REPLICAS = 8;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("boilerplate")
            .withUsername("boilerplate")
            .withPassword("boilerplate");

    @Test
    void migrate_withConcurrentReplicas_appliesMigrationsExactlyOnceWithoutError() throws Exception {
        // Each simulated replica gets its own Flyway instance and datasource,
        // mirroring how independent app instances would each run their own
        // FlywayMigrationInitializer at startup in production.
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REPLICAS)) {
            List<Callable<Void>> replicas = List.of(
                    migrationTask(), migrationTask(), migrationTask(), migrationTask(),
                    migrationTask(), migrationTask(), migrationTask(), migrationTask());

            List<Future<Void>> futures = executor.invokeAll(replicas);
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new AssertionError("replica failed to migrate", e.getCause());
                }
            }
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());

        Integer usersTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'",
                Integer.class);
        assertThat(usersTableCount).isEqualTo(1);

        // audit_log is the partitioned parent; audit_log_legacy is the default partition.
        Integer auditLogCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'audit_log'",
                Integer.class);
        assertThat(auditLogCount).isEqualTo(1);

        Integer legacyCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'audit_log_legacy'",
                Integer.class);
        assertThat(legacyCount).isEqualTo(1);

        // Exactly 3 monthly partitions were pre-created (current + next 2 months).
        Integer monthlyPartitionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class c "
                + "JOIN pg_inherits i ON c.oid = i.inhrelid "
                + "JOIN pg_class p ON i.inhparent = p.oid "
                + "WHERE p.relname = 'audit_log' AND c.relname != 'audit_log_legacy'",
                Integer.class);
        assertThat(monthlyPartitionCount).isEqualTo(3);
    }

    private static Callable<Void> migrationTask() {
        return () -> {
            Flyway.configure()
                    .dataSource(dataSource())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            return null;
        };
    }

    private static SimpleDriverDataSource dataSource() {
        try {
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass((Class<? extends Driver>) Class.forName("org.postgresql.Driver"));
            dataSource.setUrl(POSTGRES.getJdbcUrl());
            dataSource.setUsername(POSTGRES.getUsername());
            dataSource.setPassword(POSTGRES.getPassword());
            return dataSource;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("postgresql driver not found on classpath", e);
        }
    }
}
