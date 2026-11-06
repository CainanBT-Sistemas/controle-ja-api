package com.cainanbt.softwares.controleja.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailVerificationMigrationIntegrationTest {

    @Test
    void migrationVerifiesOnlyGoogleIdentityAndInvalidatesLocalSession() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            );
            Flyway.configure()
                    .dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("7"))
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            UUID localId = UUID.randomUUID();
            UUID googleId = UUID.randomUUID();
            insertUser(jdbc, localId, "local@test.com", false, null, null, "local-refresh", 3L);
            insertUser(
                    jdbc,
                    googleId,
                    "google@test.com",
                    true,
                    "GOOGLE",
                    "google-subject",
                    "google-refresh",
                    5L
            );

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertEquals(false, jdbc.queryForObject(
                    "SELECT email_verified FROM users WHERE id = ?",
                    Boolean.class,
                    localId
            ));
            assertEquals(null, jdbc.queryForObject(
                    "SELECT refresh_token FROM users WHERE id = ?",
                    String.class,
                    localId
            ));
            assertEquals(4L, jdbc.queryForObject(
                    "SELECT session_version FROM users WHERE id = ?",
                    Long.class,
                    localId
            ));
            assertEquals(true, jdbc.queryForObject(
                    "SELECT email_verified FROM users WHERE id = ?",
                    Boolean.class,
                    googleId
            ));
            assertEquals("google-refresh", jdbc.queryForObject(
                    "SELECT refresh_token FROM users WHERE id = ?",
                    String.class,
                    googleId
            ));
            assertEquals(5L, jdbc.queryForObject(
                    "SELECT session_version FROM users WHERE id = ?",
                    Long.class,
                    googleId
            ));
        }
    }

    private void insertUser(
            JdbcTemplate jdbc,
            UUID id,
            String email,
            boolean oauth2User,
            String provider,
            String providerId,
            String refreshToken,
            long sessionVersion
    ) {
        jdbc.update(
                "INSERT INTO users (id, username, password, email, enabled, account_non_expired, "
                        + "account_non_locked, credentials_non_expired, role, oauth2user, "
                        + "local_password_enabled, session_version, oauth2provider, oauth2provider_id, "
                        + "refresh_token, refresh_token_expiry, created_at) "
                        + "VALUES (?, ?, ?, ?, true, true, true, true, 'USER', ?, ?, ?, ?, ?, ?, 999999, 1)",
                id,
                "User",
                "password",
                email,
                oauth2User,
                !oauth2User,
                sessionVersion,
                provider,
                providerId,
                refreshToken
        );
    }
}
