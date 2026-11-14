package com.cainanbt.softwares.controleja.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntitlementMigrationIntegrationTest {

    @Test
    void migrationAddsSafeEntitlementDefaultsWithoutRestrictingExistingData() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            );
            Flyway.configure()
                    .dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("8"))
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            UUID userId = UUID.randomUUID();
            UUID testerId = UUID.randomUUID();
            insertUser(jdbc, userId);
            jdbc.update(
                    "INSERT INTO closed_test_testers "
                            + "(id, email, normalized_email, enabled, created_at) VALUES (?, ?, ?, true, 1)",
                    testerId,
                    "tester@controleja.test",
                    "tester@controleja.test"
            );

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertEquals("FREE", jdbc.queryForObject(
                    "SELECT plan FROM users WHERE id = ?",
                    String.class,
                    userId
            ));
            assertEquals(0L, jdbc.queryForObject(
                    "SELECT entitlement_version FROM users WHERE id = ?",
                    Long.class,
                    userId
            ));
            assertEquals("TESTER_PLUS", jdbc.queryForObject(
                    "SELECT profile FROM closed_test_testers WHERE id = ?",
                    String.class,
                    testerId
            ));
            for (String table : new String[]{"accounts", "credit_cards", "category", "vehicles"}) {
                assertEquals("false", jdbc.queryForObject(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = 'plan_restricted'",
                        String.class,
                        table
                ));
            }
        }
    }

    private void insertUser(JdbcTemplate jdbc, UUID id) {
        jdbc.update(
                "INSERT INTO users (id, username, password, email, enabled, account_non_expired, "
                        + "account_non_locked, credentials_non_expired, role, oauth2user, "
                        + "local_password_enabled, email_verified, session_version, refresh_token_expiry, created_at) "
                        + "VALUES (?, 'User', 'password', ?, true, true, true, true, 'USER', false, "
                        + "true, true, 0, 0, 1)",
                id,
                "user-" + id + "@controleja.test"
        );
    }
}
