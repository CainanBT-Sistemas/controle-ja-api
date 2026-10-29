package com.cainanbt.softwares.controleja.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountActionMigrationTest {
    @Test
    void migratesAccountActionStructureOnPostgreSql() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            flyway.migrate();
            flyway.validate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            )) {
                assertEquals(1, count(connection,
                        "SELECT count(*) FROM information_schema.tables " +
                                "WHERE table_name = 'account_action_tokens'"));
                assertEquals(1, count(connection,
                        "SELECT count(*) FROM information_schema.columns " +
                                "WHERE table_name = 'users' AND column_name = 'local_password_enabled'"));
                assertEquals(1, count(connection,
                        "SELECT count(*) FROM information_schema.columns " +
                                "WHERE table_name = 'users' AND column_name = 'session_version'"));
                assertEquals(1, count(connection,
                        "SELECT count(*) FROM pg_indexes " +
                                "WHERE indexname = 'uk_users_email_case_insensitive'"));
            }
        }
    }

    private int count(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
