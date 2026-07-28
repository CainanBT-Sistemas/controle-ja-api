package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.services.users.UserDefaultDataInitializer;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

public class UserResetControllerTest extends BaseTest {

    private static final int DEFAULT_CATEGORY_COUNT = 23;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private UserDefaultDataInitializer defaultDataInitializer;

    @Test
    @DisplayName("Deve resetar dados operacionais pelo POST canonico preservando usuario e allowlist")
    void shouldResetOperationalDataUsingCanonicalPostEndpoint() {
        RegisteredUser registered = registerAndLogin("reset_full");
        SeededData seeded = seedOperationalData(registered.userId());
        insertClosedTester("reset_full@test.com");

        given().header("Authorization", "Bearer " + registered.token())
                .when().post("/users/" + registered.userId() + "/reset")
                .then().statusCode(200)
                .body("id", is(registered.userId().toString()))
                .body("email", is("reset_full@test.com"));

        assertOperationalDataRemoved(registered.userId(), seeded);
        assertDefaultDataRecreatedOnce(registered.userId());
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", registered.userId()));
        assertEquals(1, count("SELECT COUNT(*) FROM closed_test_testers WHERE normalized_email = ?", "reset_full@test.com"));

        given().contentType(ContentType.JSON)
                .body(UserLoginDTO.builder().email("reset_full@test.com").password("123456").build())
                .post("/auth")
                .then().statusCode(200)
                .body("id", is(registered.userId().toString()));
    }

    @Test
    @DisplayName("Deve permitir segundo reset sem duplicar carteira e categorias padrao")
    void shouldResetTwiceWithoutDuplicatingDefaultData() {
        RegisteredUser registered = registerAndLogin("reset_twice");
        seedOperationalData(registered.userId());

        given().header("Authorization", "Bearer " + registered.token())
                .post("/users/" + registered.userId() + "/reset")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + registered.token())
                .post("/users/" + registered.userId() + "/reset")
                .then().statusCode(200);

        assertDefaultDataRecreatedOnce(registered.userId());
    }

    @Test
    @DisplayName("Deve fazer rollback integral se a recriacao dos dados padrao falhar")
    void shouldRollbackResetWhenDefaultDataRecreationFails() {
        RegisteredUser registered = registerAndLogin("reset_rollback");
        SeededData seeded = seedOperationalData(registered.userId());
        int transactionsBefore = count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", registered.userId());
        int accountsBefore = count("SELECT COUNT(*) FROM accounts WHERE user_id = ?", registered.userId());

        doThrow(new IllegalStateException("falha simulada no reset"))
                .when(defaultDataInitializer)
                .initialize(any());

        given().header("Authorization", "Bearer " + registered.token())
                .post("/users/" + registered.userId() + "/reset")
                .then().statusCode(500);

        reset(defaultDataInitializer);

        assertEquals(transactionsBefore, count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", registered.userId()));
        assertEquals(accountsBefore, count("SELECT COUNT(*) FROM accounts WHERE user_id = ?", registered.userId()));
        assertTrue(count("SELECT COUNT(*) FROM installment_plan WHERE id = ?", seeded.installmentId()) > 0);
        assertTrue(count("SELECT COUNT(*) FROM invoicess WHERE id = ?", seeded.invoiceId()) > 0);
        assertTrue(count("SELECT COUNT(*) FROM gas_station_rankings WHERE gas_station_id = ?", seeded.gasStationId()) > 0);
        assertTrue(count("SELECT COUNT(*) FROM vehicle_logs WHERE id = ?", seeded.vehicleLogId()) > 0);
    }

    @Test
    @DisplayName("Endpoint GET legado deve continuar compatível, mas POST segue canonico")
    void shouldKeepLegacyGetEndpointCompatible() {
        RegisteredUser registered = registerAndLogin("reset_legacy");

        given().header("Authorization", "Bearer " + registered.token())
                .get("/users/reset/" + registered.userId())
                .then().statusCode(200)
                .body("id", is(registered.userId().toString()));

        assertDefaultDataRecreatedOnce(registered.userId());
    }

    private RegisteredUser registerAndLogin(String prefix) {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername(prefix);
        user.setEmail(prefix + "@test.com");
        user.setPassword("123456");

        UUID userId = UUID.fromString(given().contentType(ContentType.JSON)
                .body(user)
                .post("/users/register")
                .then().statusCode(200)
                .extract().path("id"));

        String token = given().contentType(ContentType.JSON)
                .body(UserLoginDTO.builder().email(prefix + "@test.com").password("123456").build())
                .post("/auth")
                .then().statusCode(200)
                .extract().path("tokens.accessToken");

        return new RegisteredUser(userId, token);
    }

    private SeededData seedOperationalData(UUID userId) {
        UUID defaultAccountId = findDefaultAccount(userId);
        UUID expenseCategoryId = findCategory(userId, "DESPESA");
        UUID transferCategoryId = findCategory(userId, "TRANSFERENCIA");
        long now = System.currentTimeMillis();

        UUID accountId = UUID.randomUUID();
        UUID mirrorAccountId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID vehicleLogId = UUID.randomUUID();
        UUID gasStationId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID paymentTransactionId = UUID.randomUUID();
        UUID recurrenceRuleId = UUID.randomUUID();
        UUID transferOutId = UUID.randomUUID();
        UUID transferInId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO accounts (calculate_balance, current_balance, enabled, initial_balance, is_default, created_at, id, user_id, currency, institution, name, type)
                VALUES (true, 1000.00, true, 1000.00, false, ?, ?, ?, 'BRL', 'Banco Teste', 'Conta Reset', 'BANK')
                """, now, accountId, userId);
        jdbc.update("""
                INSERT INTO accounts (calculate_balance, current_balance, enabled, initial_balance, is_default, created_at, id, user_id, currency, institution, name, type)
                VALUES (false, 0.00, true, 0.00, false, ?, ?, ?, 'BRL', 'Cartao', 'Conta Espelho Reset', 'CREDIT_CARD')
                """, now, mirrorAccountId, userId);
        jdbc.update("""
                INSERT INTO credit_cards (best_day, close_day, current_limit, enabled, total_limit, created_at, account_id, id, user_id, name)
                VALUES (20, 10, 4000.00, true, 5000.00, ?, ?, ?, ?, 'Cartao Reset')
                """, now, mirrorAccountId, cardId, userId);
        jdbc.update("""
                INSERT INTO vehicles (current_odometer, initial_odometer, year, created_at, id, user_id, brand, model, name)
                VALUES (1000.00, 0.00, 2024, ?, ?, ?, 'Marca', 'Modelo', 'Veiculo Reset')
                """, now, vehicleId, userId);
        createVehicleLogsTableIfNeeded();
        jdbc.update("""
                INSERT INTO vehicle_logs (id, vehicle_id, created_at, description)
                VALUES (?, ?, ?, 'Log Reset')
                """, vehicleLogId, vehicleId, now);
        jdbc.update("""
                INSERT INTO gas_stations (created_at, id, user_id, name)
                VALUES (?, ?, ?, 'Posto Reset')
                """, now, gasStationId, userId);
        jdbc.update("""
                INSERT INTO gas_station_rankings (id, gas_station_id, fuel_type, refuel_count, score, updated_at)
                VALUES (?, ?, 'GASOLINA', 1, 9.0, ?)
                """, UUID.randomUUID(), gasStationId, now);
        jdbc.update("""
                INSERT INTO recurrence_rules (base_amount, created_at, start_date, account_id, category_id, id, user_id, frequency, name, status, type)
                VALUES (100.00, ?, ?, ?, ?, ?, ?, 'MONTHLY', 'Recorrencia Reset', 'ACTIVE', 'DESPESA')
                """, now, now, accountId, expenseCategoryId, recurrenceRuleId, userId);
        jdbc.update("""
                INSERT INTO invoicess (amount, enabled, month, paid, year, created_at, expiration_date, credit_card_id, id, user_id)
                VALUES (500.00, true, 7, false, 2026, ?, ?, ?, ?, ?)
                """, now, now, cardId, invoiceId, userId);
        jdbc.update("""
                INSERT INTO transactions (amount, enabled, fixed, paid, created_at, date, account_id, category_id, credit_card_id, target_invoice_id, id, user_id, name, type)
                VALUES (500.00, true, false, false, ?, ?, ?, ?, ?, ?, ?, ?, 'Compra Reset', 'DESPESA')
                """, now, now, mirrorAccountId, expenseCategoryId, cardId, invoiceId, purchaseId, userId);
        jdbc.update("""
                INSERT INTO installment_plan (amount, current_installment, enabled, fixed, paid, total_installments_plan, created_at, date, id, invoices_id, purchase_id, user_id, name, type, advance_operation_id, advanced_from_invoice_id)
                VALUES (500.00, 1, true, false, false, 1, ?, ?, ?, ?, ?, ?, 'Parcela Reset', 'DESPESA', ?, ?)
                """, now, now, installmentId, invoiceId, purchaseId, userId, UUID.randomUUID(), invoiceId);
        jdbc.update("""
                INSERT INTO transactions (amount, enabled, fixed, paid, created_at, date, account_id, category_id, target_invoice_id, id, user_id, name, type)
                VALUES (100.00, true, false, true, ?, ?, ?, ?, ?, ?, ?, 'Pagamento Reset', 'PAGAMENTO_FATURA')
                """, now, now, defaultAccountId, expenseCategoryId, invoiceId, paymentTransactionId, userId);
        jdbc.update("UPDATE invoicess SET transaction_id = ? WHERE id = ?", paymentTransactionId, invoiceId);
        jdbc.update("""
                INSERT INTO transactions (amount, enabled, fixed, paid, created_at, date, account_id, category_id, recurrence_rule_id, vehicle_id, gas_station_id, current_odometer, liters, fuel_type, efficiency, id, user_id, name, type)
                VALUES (100.00, true, false, true, ?, ?, ?, ?, ?, ?, ?, 1000.00, 40.0, 'GASOLINA', 10.0, ?, ?, 'Despesa Reset', 'DESPESA')
                """, now, now, accountId, expenseCategoryId, recurrenceRuleId, vehicleId, gasStationId, UUID.randomUUID(), userId);
        jdbc.update("""
                INSERT INTO transactions (amount, enabled, fixed, paid, created_at, date, account_id, category_id, id, user_id, name, type)
                VALUES (50.00, true, false, true, ?, ?, ?, ?, ?, ?, 'Transferencia Saida Reset', 'TRANSFERENCIA_SAIDA')
                """, now, now, accountId, transferCategoryId, transferOutId, userId);
        jdbc.update("""
                INSERT INTO transactions (amount, enabled, fixed, paid, created_at, date, account_id, category_id, parent_transaction_id, id, user_id, name, type)
                VALUES (50.00, true, false, true, ?, ?, ?, ?, ?, ?, ?, 'Transferencia Entrada Reset', 'TRANSFERENCIA_ENTRADA')
                """, now, now, defaultAccountId, transferCategoryId, transferOutId, transferInId, userId);

        assertNotEquals(0, count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId));
        return new SeededData(invoiceId, installmentId, gasStationId, vehicleLogId);
    }

    private UUID findDefaultAccount(UUID userId) {
        return jdbc.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ? AND is_default = true AND deleted_at IS NULL LIMIT 1",
                UUID.class,
                userId
        );
    }

    private UUID findCategory(UUID userId, String type) {
        return jdbc.queryForObject(
                "SELECT id FROM category WHERE user_id = ? AND category_type = ? AND is_default = true AND deleted_at IS NULL LIMIT 1",
                UUID.class,
                userId,
                type
        );
    }

    private void insertClosedTester(String email) {
        jdbc.update("""
                INSERT INTO closed_test_testers (enabled, created_at, id, email, normalized_email)
                VALUES (true, ?, ?, ?, ?)
                """, System.currentTimeMillis(), UUID.randomUUID(), email, email.toLowerCase());
    }

    private void assertOperationalDataRemoved(UUID userId, SeededData seeded) {
        assertEquals(0, count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM installment_plan WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM invoicess WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM recurrence_rules WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM credit_cards WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM vehicles WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM vehicle_logs WHERE id = ?", seeded.vehicleLogId()));
        assertEquals(0, count("SELECT COUNT(*) FROM gas_stations WHERE user_id = ?", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM gas_station_rankings WHERE gas_station_id = ?", seeded.gasStationId()));
        assertEquals(0, count("SELECT COUNT(*) FROM installment_plan WHERE id = ?", seeded.installmentId()));
        assertEquals(0, count("SELECT COUNT(*) FROM invoicess WHERE id = ?", seeded.invoiceId()));
    }

    private void assertDefaultDataRecreatedOnce(UUID userId) {
        assertEquals(1, count("SELECT COUNT(*) FROM accounts WHERE user_id = ? AND is_default = true AND deleted_at IS NULL", userId));
        assertEquals(1, count("SELECT COUNT(*) FROM accounts WHERE user_id = ? AND deleted_at IS NULL", userId));
        assertEquals(DEFAULT_CATEGORY_COUNT, count("SELECT COUNT(*) FROM category WHERE user_id = ? AND is_default = true AND deleted_at IS NULL", userId));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM category
                 WHERE user_id = ?
                   AND category_type = 'TRANSFERENCIA'
                   AND is_default = true
                   AND deleted_at IS NULL
                """, userId));
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private void createVehicleLogsTableIfNeeded() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS vehicle_logs (
                    id uuid PRIMARY KEY,
                    vehicle_id uuid NOT NULL,
                    created_at bigint,
                    description varchar(255),
                    CONSTRAINT fk_vehicle_logs_vehicle
                        FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
                )
                """);
    }

    private record RegisteredUser(UUID userId, String token) {
    }

    private record SeededData(UUID invoiceId, UUID installmentId, UUID gasStationId, UUID vehicleLogId) {
    }
}
