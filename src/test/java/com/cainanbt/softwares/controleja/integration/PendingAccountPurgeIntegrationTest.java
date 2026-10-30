package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.AccountDeletionService;
import com.cainanbt.softwares.controleja.services.UsersService;
import com.cainanbt.softwares.controleja.services.retention.PendingAccountPurgeResult;
import com.cainanbt.softwares.controleja.services.retention.PendingAccountPurgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAccountPurgeIntegrationTest extends BaseIntegrationTest {
    private static final long CUTOFF = 2_000_000_000_000L;
    private static final long OLD = CUTOFF - 1;
    private static final long RECENT = CUTOFF + 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PendingAccountPurgeService service;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private AccountDeletionService accountDeletionService;
    @Autowired
    private UsersService usersService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_user_delete ON users");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_user_delete()");
        jdbc.execute("DROP TRIGGER IF EXISTS require_token_cleanup ON users");
        jdbc.execute("DROP FUNCTION IF EXISTS require_token_cleanup()");
        jdbc.update("UPDATE transactions SET parent_transaction_id = NULL, target_invoice_id = NULL");
        jdbc.update("UPDATE invoicess SET transaction_id = NULL");
        jdbc.update("DELETE FROM installment_plan");
        jdbc.update("DELETE FROM invoicess");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM gas_station_rankings");
        jdbc.update("DELETE FROM gas_stations");
        jdbc.update("DELETE FROM recurrence_rules");
        jdbc.update("DELETE FROM credit_cards");
        jdbc.update("DELETE FROM vehicles");
        jdbc.update("DELETE FROM category");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM account_action_tokens");
        jdbc.update("DELETE FROM closed_test_testers");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void shouldPurgeOnlyAccountsAtOrBeforeTheExactCutoff() {
        UUID active = insertUser("active", null);
        UUID recent = insertUser("recent", RECENT);
        UUID exact = insertUser("exact", CUTOFF);
        UUID old = insertUser("old", OLD);

        PendingAccountPurgeResult result = service.purge(CUTOFF, false, 100, 20);

        assertEquals(2, result.users());
        assertTrue(exists("users", active));
        assertTrue(exists("users", recent));
        assertFalse(exists("users", exact));
        assertFalse(exists("users", old));
    }

    @Test
    void shouldProjectDryRunWithoutChangingUsersTokensOrOperationalData() {
        UUID userId = insertUser("dry", OLD);
        UUID accountId = insertAccount(userId);
        UUID tokenId = insertToken(userId);

        PendingAccountPurgeResult result = service.purge(CUTOFF, true, 1, 1);

        assertTrue(result.dryRun());
        assertEquals(1, result.users());
        assertEquals(1, result.accountActionTokens());
        assertTrue(exists("users", userId));
        assertTrue(exists("accounts", accountId));
        assertTrue(exists("account_action_tokens", tokenId));
    }

    @Test
    void shouldDeleteCompleteActiveGraphTokensThenUserAndPreserveOtherData() {
        UUID userId = insertUser("purged", OLD);
        UUID otherUser = insertUser("other", null);
        Graph graph = insertOperationalGraph(userId);
        UUID otherAccount = insertAccount(otherUser);
        UUID tokenId = insertToken(userId);
        UUID testerId = insertClosedTester("purged@test.local");
        installTokenOrderGuard();

        PendingAccountPurgeResult result = service.purge(CUTOFF, false, 100, 20);

        assertEquals(1, result.users());
        assertEquals(1, result.accountActionTokens());
        assertFalse(exists("users", userId));
        assertFalse(exists("account_action_tokens", tokenId));
        graph.assertDeleted(jdbc);
        assertTrue(exists("users", otherUser));
        assertTrue(exists("accounts", otherAccount));
        assertTrue(exists("closed_test_testers", testerId));
    }

    @Test
    void shouldRollbackWholeBatchWhenHardDeleteFails() {
        UUID userId = insertUser("rollback", OLD);
        UUID accountId = insertAccount(userId);
        UUID tokenId = insertToken(userId);
        jdbc.execute("""
                CREATE FUNCTION fail_user_delete() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced purge failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_user_delete
                BEFORE DELETE ON users
                FOR EACH STATEMENT EXECUTE FUNCTION fail_user_delete()
                """);

        assertThrows(RuntimeException.class, () -> service.purge(CUTOFF, false, 100, 20));

        assertTrue(exists("users", userId));
        assertTrue(exists("accounts", accountId));
        assertTrue(exists("account_action_tokens", tokenId));
    }

    @Test
    void shouldRespectBatchLimitsAndBeIdempotent() {
        for (int index = 0; index < 5; index++) {
            insertUser("batch-" + index, OLD - index);
        }

        PendingAccountPurgeResult limited = service.purge(CUTOFF, false, 2, 2);
        PendingAccountPurgeResult remaining = service.purge(CUTOFF, false, 2, 2);
        PendingAccountPurgeResult idempotent = service.purge(CUTOFF, false, 2, 2);

        assertEquals(4, limited.users());
        assertEquals(2, limited.batches());
        assertEquals(1, remaining.users());
        assertEquals(0, idempotent.users());
        assertEquals(0, count("SELECT COUNT(*) FROM users"));
    }

    @Test
    void shouldIgnoreAccountReactivatedBeforeCandidateLock() {
        UUID userId = insertUser("reactivated", OLD);
        transactionTemplate.executeWithoutResult(status -> {
            var user = usersRepository.findAnyByEmailForUpdate("reactivated@test.local").orElseThrow();
            accountDeletionService.reactivate(user);
            usersRepository.saveAndFlush(user);
        });

        PendingAccountPurgeResult result = service.purge(CUTOFF, false, 100, 20);

        assertEquals(0, result.users());
        assertTrue(exists("users", userId));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL", userId));
    }

    @Test
    void concurrentReactivationAndPurgeMustEndInOneValidState() throws Exception {
        UUID userId = insertUser("race", OLD);
        UUID accountId = insertAccount(userId);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var purge = executor.submit(() -> {
                await(start);
                service.purge(CUTOFF, false, 100, 20);
            });
            var reactivate = executor.submit(() -> {
                await(start);
                transactionTemplate.executeWithoutResult(status -> usersRepository
                        .findAnyByEmailForUpdate("race@test.local")
                        .ifPresent(user -> {
                            accountDeletionService.reactivate(user);
                            usersRepository.saveAndFlush(user);
                        }));
            });
            start.countDown();
            purge.get();
            reactivate.get();
        } finally {
            executor.shutdownNow();
        }

        boolean purged = !exists("users", userId) && !exists("accounts", accountId);
        boolean reactivated = exists("users", userId)
                && exists("accounts", accountId)
                && count("SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NULL", userId) == 1;
        assertTrue(purged || reactivated);
    }

    @Test
    void shouldAllowRegisteringTheSameEmailAfterPhysicalDeletion() {
        UUID oldUserId = insertUser("reuse", OLD);
        service.purge(CUTOFF, false, 100, 20);

        InsertUpdateUserDTO request = new InsertUpdateUserDTO();
        request.setUsername("new-user");
        request.setEmail("reuse@test.local");
        request.setPassword("123456");
        var recreated = usersService.createNewUser(request, new MockHttpServletRequest());

        assertNotEquals(oldUserId, recreated.getId());
        assertFalse(exists("users", oldUserId));
        assertTrue(exists("users", recreated.getId()));
    }

    private Graph insertOperationalGraph(UUID userId) {
        UUID accountId = insertAccount(userId);
        UUID categoryId = insertCategory(userId);
        UUID cardId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO credit_cards (
                    id, user_id, account_id, name, total_limit, current_limit,
                    close_day, best_day, enabled, created_at
                ) VALUES (?, ?, ?, ?, 1000, 1000, 1, 10, true, 1)
                """, cardId, userId, accountId, "card-" + cardId);
        UUID vehicleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO vehicles (
                    id, user_id, name, brand, model, year, current_odometer,
                    initial_odometer, created_at
                ) VALUES (?, ?, ?, 'Brand', 'Model', 2026, 0, 0, 1)
                """, vehicleId, userId, "vehicle-" + vehicleId);
        UUID stationId = UUID.randomUUID();
        jdbc.update("INSERT INTO gas_stations (id, user_id, name, created_at) VALUES (?, ?, ?, 1)",
                stationId, userId, "station-" + stationId);
        UUID rankingId = UUID.randomUUID();
        jdbc.update("INSERT INTO gas_station_rankings (id, gas_station_id, fuel_type) VALUES (?, ?, 'GASOLINA')",
                rankingId, stationId);
        UUID recurrenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recurrence_rules (
                    id, user_id, account_id, category_id, name, base_amount, type,
                    frequency, start_date, status, created_at
                ) VALUES (?, ?, ?, ?, ?, 10, 'DESPESA', 'MONTHLY', 1, 'ACTIVE', 1)
                """, recurrenceId, userId, accountId, categoryId, "recurrence-" + recurrenceId);
        UUID transactionId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO transactions (
                            id, user_id, account_id, category_id, credit_card_id,
                            recurrence_rule_id, vehicle_id, gas_station_id, date, name, type,
                            amount, fixed, paid, enabled, full_tank, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'DESPESA', 10, false, false, true, false, 1)
                        """, transactionId, userId, accountId, categoryId, cardId, recurrenceId,
                vehicleId, stationId, "transaction-" + transactionId);
        UUID invoiceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invoicess (
                    id, user_id, credit_card_id, transaction_id, amount, month, year,
                    expiration_date, paid, enabled, created_at
                ) VALUES (?, ?, ?, ?, 10, 1, 2026, 1, false, true, 1)
                """, invoiceId, userId, cardId, transactionId);
        jdbc.update("UPDATE transactions SET target_invoice_id = ? WHERE id = ?", invoiceId, transactionId);
        UUID installmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO installment_plan (
                    id, user_id, invoices_id, purchase_id, date, name, type, amount,
                    total_installments_plan, current_installment, fixed, paid, enabled, created_at
                ) VALUES (?, ?, ?, ?, 1, ?, 'DESPESA', 10, 1, 1, false, false, true, 1)
                """, installmentId, userId, invoiceId, UUID.randomUUID(), "installment-" + installmentId);
        return new Graph(accountId, categoryId, cardId, vehicleId, stationId, rankingId,
                recurrenceId, transactionId, invoiceId, installmentId);
    }

    private UUID insertUser(String prefix, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO users (
                            id, username, email, password, role, enabled, account_non_expired,
                            account_non_locked, credentials_non_expired, oauth2user,
                            local_password_enabled, session_version, refresh_token_expiry,
                            created_at, deleted_at
                        ) VALUES (?, ?, ?, ?, 'USER', ?, true, ?, true, false, true, 0, 0, 1, ?)
                        """, id, prefix, prefix + "@test.local", "password", deletedAt == null,
                deletedAt == null, deletedAt);
        return id;
    }

    private UUID insertAccount(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (
                    id, user_id, type, name, institution, currency, current_balance,
                    calculate_balance, initial_balance, is_default, enabled, created_at
                ) VALUES (?, ?, 'BANK', ?, 'Test', 'BRL', 0, true, 0, false, true, 1)
                """, id, userId, "account-" + id);
        return id;
    }

    private UUID insertCategory(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO category (
                    id, user_id, name, category_type, enabled, is_sub_category,
                    is_default, created_at
                ) VALUES (?, ?, ?, 'DESPESA', true, false, false, 1)
                """, id, userId, "category-" + id);
        return id;
    }

    private UUID insertToken(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO account_action_tokens (
                            id, user_id, purpose, email_hash, ip_hash, code_digest,
                            expires_at, attempts, max_attempts, created_at, version
                        ) VALUES (?, ?, 'ACCOUNT_REACTIVATION', ?, ?, ?, 10, 0, 5, 1, 0)
                        """, id, userId, UUID.randomUUID().toString().replace("-", "").repeat(2),
                "i".repeat(64), "c".repeat(64));
        return id;
    }

    private UUID insertClosedTester(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO closed_test_testers (id, email, normalized_email, enabled, created_at)
                VALUES (?, ?, ?, true, 1)
                """, id, email, email);
        return id;
    }

    private void installTokenOrderGuard() {
        jdbc.execute("""
                CREATE FUNCTION require_token_cleanup() RETURNS trigger AS $$
                BEGIN
                    IF EXISTS (SELECT 1 FROM account_action_tokens WHERE user_id = OLD.id) THEN
                        RAISE EXCEPTION 'tokens must be deleted first';
                    END IF;
                    RETURN OLD;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER require_token_cleanup
                BEFORE DELETE ON users
                FOR EACH ROW EXECUTE FUNCTION require_token_cleanup()
                """);
    }

    private boolean exists(String table, UUID id) {
        return count("SELECT COUNT(*) FROM " + table + " WHERE id = ?", id) == 1;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Graph(
            UUID account,
            UUID category,
            UUID card,
            UUID vehicle,
            UUID station,
            UUID ranking,
            UUID recurrence,
            UUID transaction,
            UUID invoice,
            UUID installment
    ) {
        void assertDeleted(JdbcTemplate jdbc) {
            assertEquals(0, count(jdbc, "accounts", account));
            assertEquals(0, count(jdbc, "category", category));
            assertEquals(0, count(jdbc, "credit_cards", card));
            assertEquals(0, count(jdbc, "vehicles", vehicle));
            assertEquals(0, count(jdbc, "gas_stations", station));
            assertEquals(0, count(jdbc, "gas_station_rankings", ranking));
            assertEquals(0, count(jdbc, "recurrence_rules", recurrence));
            assertEquals(0, count(jdbc, "transactions", transaction));
            assertEquals(0, count(jdbc, "invoicess", invoice));
            assertEquals(0, count(jdbc, "installment_plan", installment));
        }

        private static int count(JdbcTemplate jdbc, String table, UUID id) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
                    Integer.class,
                    id
            );
        }
    }
}
