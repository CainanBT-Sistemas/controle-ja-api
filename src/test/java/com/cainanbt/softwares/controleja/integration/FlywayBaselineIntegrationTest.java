package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.ControlejaApplication;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayBaselineIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("controleja_flyway")
                    .withUsername("controleja")
                    .withPassword("controleja");

    private static final Set<String> DOMAIN_TABLES = Set.of(
            "users",
            "accounts",
            "category",
            "credit_cards",
            "vehicles",
            "gas_stations",
            "gas_station_rankings",
            "recurrence_rules",
            "transactions",
            "invoicess",
            "installment_plan",
            "closed_test_testers"
    );

    private static final Map<String, Set<String>> EXPECTED_COLUMNS = expectedColumns();

    private static final Set<String> EXPECTED_FOREIGN_KEYS = Set.of(
            "fk_accounts_user",
            "fk_category_parent",
            "fk_category_user",
            "fk_credit_cards_account",
            "fk_credit_cards_user",
            "fk_vehicles_user",
            "fk_gas_stations_user",
            "fk_gas_station_rankings_station",
            "fk_recurrence_rules_account",
            "fk_recurrence_rules_category",
            "fk_recurrence_rules_target_account",
            "fk_recurrence_rules_user",
            "fk_transactions_account",
            "fk_transactions_category",
            "fk_transactions_credit_card",
            "fk_transactions_gas_station",
            "fk_transactions_parent",
            "fk_transactions_recurrence_rule",
            "fk_transactions_user",
            "fk_transactions_vehicle",
            "fk_invoicess_credit_card",
            "fk_invoicess_transaction",
            "fk_invoicess_user",
            "fk_transactions_target_invoice",
            "fk_installment_plan_invoice",
            "fk_installment_plan_user",
            "fk_installment_advanced_from_invoice"
    );

    private static final Set<String> EXPECTED_INDEXES = Set.of(
            "idx_users_email_deleted",
            "idx_users_enabled_locked_deleted",
            "idx_accounts_user_deleted_type",
            "idx_accounts_user_name_type_deleted",
            "idx_category_user_deleted_type",
            "idx_category_parent_deleted",
            "idx_category_user_name_deleted",
            "idx_credit_cards_user_deleted",
            "idx_credit_cards_account_deleted",
            "idx_vehicles_user_deleted",
            "idx_vehicles_user_plate_deleted",
            "idx_gas_stations_user_deleted",
            "idx_gas_stations_user_name_deleted",
            "idx_gas_rankings_station_fuel",
            "idx_gas_rankings_score",
            "idx_recurrence_user_status_deleted",
            "idx_recurrence_status_deleted",
            "idx_recurrence_account_deleted",
            "idx_transactions_user_deleted_date",
            "idx_transactions_user_type_paid_date",
            "idx_transactions_recurrence_paid_date",
            "idx_transactions_parent_deleted",
            "idx_transactions_vehicle_date",
            "idx_transactions_credit_card_date",
            "idx_transactions_target_invoice",
            "idx_invoices_user_expiration_deleted",
            "idx_invoices_user_card_expiration",
            "idx_invoices_card_month_year",
            "idx_invoices_user_paid_amount_expiration",
            "idx_installments_invoice_user_deleted_date",
            "idx_installments_purchase_user_deleted",
            "idx_installments_user_date_deleted",
            "idx_installments_invoice_paid_amount",
            "idx_installments_advance_operation",
            "idx_closed_test_testers_normalized_enabled"
    );

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void shouldCreateValidateUseAndReuseTheSchemaWithoutReapplyingMigrations() {
        UUID userId;
        long historyInstalledRank;
        long historyInstalledOn;

        try (ConfigurableApplicationContext firstContext = startApplication()) {
            configureRestAssured(firstContext);
            JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
            Flyway flyway = firstContext.getBean(Flyway.class);

            assertMigrationAppliedSuccessfully(flyway, jdbc);
            assertJpaRecognizesAllEntities(firstContext);
            assertSchemaObjects(jdbc);

            userId = registerAndAssertDefaultData(jdbc);
            String token = login();
            performAuthenticatedAccountCrud(token);

            historyInstalledRank = jdbc.queryForObject(
                    "SELECT installed_rank FROM flyway_schema_history WHERE version = '2'",
                    Long.class
            );
            historyInstalledOn = jdbc.queryForObject(
                    "SELECT EXTRACT(EPOCH FROM installed_on)::bigint "
                            + "FROM flyway_schema_history WHERE version = '2'",
                    Long.class
            );
        }

        try (ConfigurableApplicationContext secondContext = startApplication()) {
            JdbcTemplate jdbc = secondContext.getBean(JdbcTemplate.class);
            Flyway flyway = secondContext.getBean(Flyway.class);

            assertMigrationAppliedSuccessfully(flyway, jdbc);
            assertEquals(
                    historyInstalledRank,
                    jdbc.queryForObject(
                            "SELECT installed_rank FROM flyway_schema_history WHERE version = '2'",
                            Long.class
                    )
            );
            assertEquals(
                    historyInstalledOn,
                    jdbc.queryForObject(
                            "SELECT EXTRACT(EPOCH FROM installed_on)::bigint "
                                    + "FROM flyway_schema_history WHERE version = '2'",
                            Long.class
                    )
            );
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE id = ?",
                            Integer.class,
                            userId
                    )
            );
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM accounts WHERE user_id = ? AND is_default = true",
                            Integer.class,
                            userId
                    )
            );
            assertEquals(
                    23,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM category WHERE user_id = ? AND is_default = true",
                            Integer.class,
                            userId
                    )
            );
        }
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(ControlejaApplication.class)
                .run(
                        "--spring.profiles.active=homolog",
                        "--server.port=0",
                        "--DB_URL=" + POSTGRES.getJdbcUrl(),
                        "--DB_USERNAME=" + POSTGRES.getUsername(),
                        "--DB_PASSWORD=" + POSTGRES.getPassword(),
                        "--GOOGLE_CLIENT_ID=test-client.apps.googleusercontent.com",
                        "--GOOGLE_CLIENT_SECRET=test-secret",
                        "--JWT_SECRET=flyway-test-secret-that-is-long-enough-for-hmac"
                );
    }

    private void configureRestAssured(ConfigurableApplicationContext context) {
        ServletWebServerApplicationContext webContext =
                (ServletWebServerApplicationContext) context;
        RestAssured.port = webContext.getWebServer().getPort();
        RestAssured.basePath = "/controle_ja_api/v1";
    }

    private void assertMigrationAppliedSuccessfully(Flyway flyway, JdbcTemplate jdbc) {
        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("4", current.getVersion().getVersion());
        assertEquals(MigrationState.SUCCESS, current.getState());
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE version IN ('1', '2', '3', '4') AND success = true",
                        Integer.class
                )
        );
    }

    private void assertJpaRecognizesAllEntities(ConfigurableApplicationContext context) {
        EntityManagerFactory entityManagerFactory =
                context.getBean(EntityManagerFactory.class);
        Set<String> entityNames = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getSimpleName())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "Users",
                        "Accounts",
                        "Category",
                        "CreditCard",
                        "Vehicle",
                        "GasStation",
                        "GasStationRanking",
                        "RecurrenceRule",
                        "Transactions",
                        "Invoices",
                        "InstallmentPlan",
                        "ClosedTestTester"
                ),
                entityNames
        );
    }

    private void assertSchemaObjects(JdbcTemplate jdbc) {
        Set<String> tables = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public'",
                String.class
        ));
        assertTrue(tables.containsAll(DOMAIN_TABLES));
        assertTrue(tables.contains("flyway_schema_history"));

        EXPECTED_COLUMNS.forEach((table, expectedColumns) -> {
            Set<String> actualColumns = Set.copyOf(jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    String.class,
                    table
            ));
            assertEquals(expectedColumns, actualColumns, "Columns differ for " + table);
        });

        Set<String> foreignKeys = Set.copyOf(jdbc.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = 'public' "
                        + "AND constraint_type = 'FOREIGN KEY'",
                String.class
        ));
        assertEquals(EXPECTED_FOREIGN_KEYS, foreignKeys);

        Set<String> indexes = Set.copyOf(jdbc.queryForList(
                "SELECT indexname FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND indexname LIKE 'idx_%'",
                String.class
        ));
        assertEquals(EXPECTED_INDEXES, indexes);

        assertEquals(
                "NO",
                jdbc.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = 'public' "
                                + "AND table_name = 'accounts' "
                                + "AND column_name = 'current_balance'",
                        String.class
                )
        );
        assertEquals(
                "38",
                jdbc.queryForObject(
                        "SELECT numeric_precision::text FROM information_schema.columns "
                                + "WHERE table_schema = 'public' "
                                + "AND table_name = 'accounts' "
                                + "AND column_name = 'current_balance'",
                        String.class
                )
        );
        assertEquals(
                "2",
                jdbc.queryForObject(
                        "SELECT numeric_scale::text FROM information_schema.columns "
                                + "WHERE table_schema = 'public' "
                                + "AND table_name = 'accounts' "
                                + "AND column_name = 'current_balance'",
                        String.class
                )
        );
        assertEquals(
                "false",
                jdbc.queryForObject(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_schema = 'public' "
                                + "AND table_name = 'accounts' "
                                + "AND column_name = 'is_default'",
                        String.class
                )
        );
    }

    private UUID registerAndAssertDefaultData(JdbcTemplate jdbc) {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("flyway_user");
        user.setEmail("flyway@controleja.local");
        user.setPassword("123456");

        String userId = given()
                .contentType(ContentType.JSON)
                .body(user)
                .when().post("/users/register")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract().path("id");

        UUID id = UUID.fromString(userId);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM accounts WHERE user_id = ? "
                                + "AND name = 'Minha Carteira' AND is_default = true",
                        Integer.class,
                        id
                )
        );
        assertEquals(
                23,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM category WHERE user_id = ? AND is_default = true",
                        Integer.class,
                        id
                )
        );
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM category "
                                + "WHERE user_id = ? AND category_type = 'TRANSFERENCIA' "
                                + "AND is_default = true AND enabled = true AND deleted_at IS NULL",
                        Integer.class,
                        id
                )
        );
        return id;
    }

    private String login() {
        UserLoginDTO login = UserLoginDTO.builder()
                .email("flyway@controleja.local")
                .password("123456")
                .build();

        return given()
                .contentType(ContentType.JSON)
                .body(login)
                .when().post("/auth")
                .then()
                .statusCode(200)
                .body("tokens.accessToken", notNullValue())
                .body("tokens.refreshToken", notNullValue())
                .extract().path("tokens.accessToken");
    }

    private void performAuthenticatedAccountCrud(String token) {
        AccountDTO account = new AccountDTO();
        account.setName("Conta Flyway");
        account.setType(AccountType.BANK);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setInstitution("Banco Teste");
        account.setCalculateBalance(true);

        String accountId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(account)
                .when().post("/accounts")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/accounts")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)));

        account.setName("Conta Flyway Atualizada");
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(account)
                .when().put("/accounts/" + accountId)
                .then()
                .statusCode(200)
                .body("name", is("Conta Flyway Atualizada"));

        given()
                .header("Authorization", "Bearer " + token)
                .when().delete("/accounts/" + accountId)
                .then()
                .statusCode(200);
    }

    private static Map<String, Set<String>> expectedColumns() {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        columns.put("users", Set.of(
                "account_non_expired",
                "account_non_locked",
                "credentials_non_expired",
                "enabled",
                "oauth2user",
                "created_at",
                "deleted_at",
                "refresh_token_expiry",
                "updated_at",
                "id",
                "email",
                "last_ip",
                "last_user_agent",
                "oauth2provider",
                "oauth2provider_id",
                "password",
                "refresh_token",
                "role",
                "username"
        ));
        columns.put("accounts", Set.of(
                "calculate_balance",
                "current_balance",
                "enabled",
                "initial_balance",
                "is_default",
                "created_at",
                "deleted_at",
                "updated_at",
                "id",
                "user_id",
                "color",
                "currency",
                "icon",
                "institution",
                "name",
                "type"
        ));
        columns.put("category", Set.of(
                "enabled",
                "is_default",
                "is_sub_category",
                "created_at",
                "deleted_at",
                "updated_at",
                "id",
                "sub_category_id",
                "user_id",
                "category_type",
                "color",
                "icon",
                "name"
        ));
        columns.put("credit_cards", Set.of(
                "best_day",
                "close_day",
                "current_limit",
                "enabled",
                "total_limit",
                "created_at",
                "deleted_at",
                "updated_at",
                "account_id",
                "id",
                "user_id",
                "color",
                "description",
                "icon",
                "name"
        ));
        columns.put("vehicles", Set.of(
                "avg_km_per_liter_ethanol",
                "avg_km_per_liter_gasoline",
                "current_odometer",
                "initial_odometer",
                "tank_capacity",
                "year",
                "created_at",
                "deleted_at",
                "updated_at",
                "id",
                "user_id",
                "brand",
                "model",
                "name",
                "plate"
        ));
        columns.put("gas_stations", Set.of(
                "created_at",
                "deleted_at",
                "updated_at",
                "id",
                "user_id",
                "address",
                "city",
                "name",
                "state"
        ));
        columns.put("gas_station_rankings", Set.of(
                "adjusted_avg_kml",
                "avg_cost_per_km",
                "avg_kml",
                "city_refuel_count",
                "last_price_per_liter",
                "refuel_count",
                "road_refuel_count",
                "score",
                "total_adjusted_distance",
                "total_amount",
                "total_distance",
                "total_liters",
                "unknown_refuel_count",
                "updated_at",
                "gas_station_id",
                "id",
                "fuel_type"
        ));
        columns.put("recurrence_rules", Set.of(
                "base_amount",
                "created_at",
                "deleted_at",
                "end_date",
                "start_date",
                "updated_at",
                "account_id",
                "category_id",
                "id",
                "target_account_id",
                "user_id",
                "description",
                "frequency",
                "name",
                "status",
                "type"
        ));
        columns.put("transactions", Set.of(
                "amount",
                "current_odometer",
                "efficiency",
                "enabled",
                "fixed",
                "full_tank",
                "liters",
                "paid",
                "created_at",
                "date",
                "deleted_at",
                "updated_at",
                "account_id",
                "category_id",
                "credit_card_id",
                "gas_station_id",
                "id",
                "parent_transaction_id",
                "recurrence_rule_id",
                "target_invoice_id",
                "user_id",
                "vehicle_id",
                "description",
                "driving_predominance",
                "fuel_type",
                "name",
                "type"
        ));
        columns.put("invoicess", Set.of(
                "amount",
                "enabled",
                "month",
                "paid",
                "year",
                "created_at",
                "deleted_at",
                "expiration_date",
                "updated_at",
                "credit_card_id",
                "id",
                "transaction_id",
                "user_id"
        ));
        columns.put("installment_plan", Set.of(
                "amount",
                "current_installment",
                "enabled",
                "fixed",
                "paid",
                "total_installments_plan",
                "created_at",
                "date",
                "deleted_at",
                "updated_at",
                "id",
                "invoices_id",
                "purchase_id",
                "user_id",
                "advance_operation_id",
                "advanced_from_invoice_id",
                "advance_corrected_at",
                "description",
                "name",
                "type"
        ));
        columns.put("closed_test_testers", Set.of(
                "enabled",
                "created_at",
                "disabled_at",
                "updated_at",
                "id",
                "email",
                "normalized_email",
                "reason"
        ));
        return Map.copyOf(columns);
    }
}
