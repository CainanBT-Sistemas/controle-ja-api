package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import com.cainanbt.softwares.controleja.repositories.SoftDeletedDataPurgeRepository;
import com.cainanbt.softwares.controleja.services.retention.PurgeRunResult;
import com.cainanbt.softwares.controleja.services.retention.SoftDeletedDataPurgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDeletedDataPurgeIntegrationTest extends BaseIntegrationTest {
    private static final long CUTOFF = 2_000_000_000_000L;
    private static final long OLD = CUTOFF - 1;
    private static final long RECENT = CUTOFF + 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private SoftDeletedDataPurgeService service;
    @Autowired
    private SoftDeletedDataPurgeRepository repository;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_transaction_delete ON transactions");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_transaction_delete()");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_account_delete ON accounts");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_account_delete()");
        jdbc.execute("DROP TABLE IF EXISTS vehicle_logs");
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
    void shouldRespectCutoffPreserveExcludedTablesAndBeIdempotent() {
        UUID userId = insertUser();
        UUID active = insertAccount(userId, null);
        UUID recent = insertAccount(userId, RECENT);
        UUID exact = insertAccount(userId, CUTOFF);
        UUID old = insertAccount(userId, OLD);
        UUID tokenId = insertAccountActionToken(userId);
        UUID testerId = insertClosedTester();

        PurgeRunResult first = service.purge(CUTOFF, false, 100, 20);

        assertEquals(2L, first.getCounts().get("accounts"));
        assertTrue(exists("accounts", active));
        assertTrue(exists("accounts", recent));
        assertFalse(exists("accounts", exact));
        assertFalse(exists("accounts", old));
        assertTrue(exists("users", userId));
        assertTrue(exists("account_action_tokens", tokenId));
        assertTrue(exists("closed_test_testers", testerId));

        PurgeRunResult second = service.purge(CUTOFF, false, 100, 20);
        assertEquals(0L, second.getTotalCount());
    }

    @Test
    void shouldPreserveActiveFinancialTotalsAndAccountBalance() {
        UUID userId = insertUser();
        UUID activeAccount = insertAccount(userId, null);
        UUID activeCategory = insertCategory(userId, null, null);
        UUID activeTransaction = insertTransaction(
                userId,
                activeAccount,
                activeCategory,
                null,
                null,
                null,
                null,
                null,
                null
        );
        UUID expiredAccount = insertAccount(userId, OLD);
        UUID expiredCategory = insertCategory(userId, null, OLD);
        UUID expiredTransaction = insertTransaction(
                userId,
                expiredAccount,
                expiredCategory,
                null,
                null,
                null,
                null,
                null,
                OLD
        );
        BigDecimal activeTotalBefore = activeTransactionTotal(userId);
        BigDecimal balanceBefore = accountBalance(activeAccount);

        service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", activeTransaction));
        assertFalse(exists("transactions", expiredTransaction));
        assertEquals(0, activeTotalBefore.compareTo(activeTransactionTotal(userId)));
        assertEquals(0, balanceBefore.compareTo(accountBalance(activeAccount)));
    }

    @Test
    void shouldDeleteEligibleChildrenAndParent() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, OLD, OLD);

        PurgeRunResult result = service.purge(CUTOFF, false, 100, 20);

        assertFalse(exists("transactions", hierarchy.parentId()));
        hierarchy.childIds().forEach(id -> assertFalse(exists("transactions", id)));
        assertEquals(3L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldKeepParentWhenChildIsRecent() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, RECENT);

        PurgeRunResult result = service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        assertTrue(exists("transactions", hierarchy.childIds().get(0)));
        assertEquals(0L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldKeepParentWhenChildIsActive() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, (Long) null);

        PurgeRunResult result = service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        assertTrue(exists("transactions", hierarchy.childIds().get(0)));
        assertEquals(0L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldDeleteEligibleChildButKeepParentBlockedByActiveChild() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, OLD, null);

        PurgeRunResult result = service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        assertFalse(exists("transactions", hierarchy.childIds().get(0)));
        assertTrue(exists("transactions", hierarchy.childIds().get(1)));
        assertEquals(1L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldDeleteEligibleChildButKeepParentBlockedByRecentChild() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, OLD, RECENT);

        PurgeRunResult result = service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        assertFalse(exists("transactions", hierarchy.childIds().get(0)));
        assertTrue(exists("transactions", hierarchy.childIds().get(1)));
        assertEquals(1L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldDeleteParentAfterLastChildBecomesEligible() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, RECENT);

        PurgeRunResult first = service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        assertTrue(exists("transactions", hierarchy.childIds().get(0)));
        assertEquals(0L, first.getCounts().get("transactions"));

        PurgeRunResult second = service.purge(RECENT, false, 100, 20);

        assertFalse(exists("transactions", hierarchy.parentId()));
        assertFalse(exists("transactions", hierarchy.childIds().get(0)));
        assertEquals(2L, second.getCounts().get("transactions"));
    }

    @Test
    void shouldProcessEligibleChildBeforeParentAcrossSmallBatches() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD - 10, OLD);

        PurgeRunResult result = service.purge(CUTOFF, false, 1, 10);

        assertFalse(exists("transactions", hierarchy.childIds().get(0)));
        assertFalse(exists("transactions", hierarchy.parentId()));
        assertEquals(2L, result.getCounts().get("transactions"));
    }

    @Test
    void shouldProjectHierarchyWithoutDeletingDuringDryRun() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, OLD, OLD);

        PurgeRunResult projection = service.purge(CUTOFF, true, 100, 20);

        assertTrue(exists("transactions", hierarchy.parentId()));
        hierarchy.childIds().forEach(id -> assertTrue(exists("transactions", id)));
        assertEquals(3L, projection.getCounts().get("transactions"));

        PurgeRunResult execution = service.purge(CUTOFF, false, 100, 20);

        assertFalse(exists("transactions", hierarchy.parentId()));
        hierarchy.childIds().forEach(id -> assertFalse(exists("transactions", id)));
        assertEquals(
                projection.getCounts().get("transactions"),
                execution.getCounts().get("transactions")
        );
    }

    @Test
    void shouldRollbackParentAndChildrenWhenBatchFails() {
        TransactionHierarchy hierarchy = insertTransactionHierarchy(OLD, OLD, OLD);
        jdbc.execute("""
                CREATE FUNCTION fail_transaction_delete() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced transaction retention failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_transaction_delete
                BEFORE DELETE ON transactions
                FOR EACH STATEMENT EXECUTE FUNCTION fail_transaction_delete()
                """);

        assertThrows(RuntimeException.class, () -> service.purge(CUTOFF, false, 100, 20));

        assertTrue(exists("transactions", hierarchy.parentId()));
        hierarchy.childIds().forEach(id -> assertTrue(exists("transactions", id)));
    }

    @Test
    void shouldSkipBlockedOldCandidateAndContinueWithLaterEligibleRows() {
        UUID userId = insertUser();
        UUID blockedAccount = insertAccount(userId, OLD - 10);
        UUID removableAccount = insertAccount(userId, OLD);
        UUID categoryId = insertCategory(userId, null, OLD);
        UUID recurrenceId = insertRecurrenceRule(userId, blockedAccount, categoryId, OLD);
        UUID oldParentTransaction = insertTransaction(
                userId,
                blockedAccount,
                categoryId,
                null,
                null,
                recurrenceId,
                null,
                null,
                OLD
        );
        UUID activeTransaction = insertTransaction(
                userId,
                blockedAccount,
                categoryId,
                null,
                oldParentTransaction,
                recurrenceId,
                null,
                null,
                null
        );

        PurgeRunResult result = service.purge(CUTOFF, false, 1, 5);

        assertTrue(exists("accounts", blockedAccount));
        assertFalse(exists("accounts", removableAccount));
        assertTrue(exists("transactions", oldParentTransaction));
        assertTrue(exists("transactions", activeTransaction));
        assertTrue(exists("recurrence_rules", recurrenceId));
        assertTrue(exists("category", categoryId));
        assertEquals(1L, result.getCounts().get("accounts"));
        assertEquals(1, count("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL"));
    }

    @Test
    void shouldDeleteCompleteInvoiceCycleIncludingAdvancedInvoiceReference() {
        UUID userId = insertUser();
        UUID accountId = insertAccount(userId, OLD);
        UUID categoryId = insertCategory(userId, null, OLD);
        UUID cardId = insertCreditCard(userId, accountId, OLD);
        UUID transactionId = insertTransaction(
                userId,
                accountId,
                categoryId,
                cardId,
                null,
                null,
                null,
                null,
                OLD
        );
        UUID invoiceId = insertInvoice(userId, cardId, transactionId, OLD);
        jdbc.update("UPDATE transactions SET target_invoice_id = ? WHERE id = ?", invoiceId, transactionId);
        UUID installmentId = insertInstallment(userId, invoiceId, invoiceId, OLD);

        service.purge(CUTOFF, false, 100, 20);

        assertFalse(exists("installment_plan", installmentId));
        assertFalse(exists("transactions", transactionId));
        assertFalse(exists("invoicess", invoiceId));
        assertFalse(exists("credit_cards", cardId));
        assertFalse(exists("category", categoryId));
        assertFalse(exists("accounts", accountId));
        assertTrue(exists("users", userId));
    }

    @Test
    void shouldKeepPartiallyExpiredCycleAndParentWithRecentDependent() {
        UUID userId = insertUser();
        UUID accountId = insertAccount(userId, OLD);
        UUID categoryId = insertCategory(userId, null, OLD);
        UUID cardId = insertCreditCard(userId, accountId, OLD);
        UUID transactionId = insertTransaction(
                userId,
                accountId,
                categoryId,
                cardId,
                null,
                null,
                null,
                null,
                OLD
        );
        UUID invoiceId = insertInvoice(userId, cardId, transactionId, RECENT);
        jdbc.update("UPDATE transactions SET target_invoice_id = ? WHERE id = ?", invoiceId, transactionId);
        UUID installmentId = insertInstallment(userId, invoiceId, invoiceId, RECENT);

        service.purge(CUTOFF, false, 100, 20);

        assertTrue(exists("installment_plan", installmentId));
        assertTrue(exists("transactions", transactionId));
        assertTrue(exists("invoicess", invoiceId));
        assertTrue(exists("credit_cards", cardId));
        assertTrue(exists("accounts", accountId));
    }

    @Test
    void shouldDeleteCategoryChildrenRankingsAndLegacyVehicleLogsBeforeParents() {
        UUID userId = insertUser();
        UUID parentCategory = insertCategory(userId, null, OLD);
        UUID childCategory = insertCategory(userId, parentCategory, OLD);
        UUID blockedParentCategory = insertCategory(userId, null, OLD - 10);
        UUID recentChildCategory = insertCategory(userId, blockedParentCategory, RECENT);
        UUID stationId = insertGasStation(userId, OLD);
        UUID rankingId = insertGasStationRanking(stationId);
        UUID vehicleId = insertVehicle(userId, OLD);
        createVehicleLogsTable();
        UUID vehicleLogId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO vehicle_logs (id, vehicle_id, created_at, description) VALUES (?, ?, ?, ?)",
                vehicleLogId,
                vehicleId,
                1L,
                "legacy"
        );

        service.purge(CUTOFF, false, 100, 20);

        assertFalse(exists("category", childCategory));
        assertFalse(exists("category", parentCategory));
        assertTrue(exists("category", recentChildCategory));
        assertTrue(exists("category", blockedParentCategory));
        assertFalse(exists("gas_station_rankings", rankingId));
        assertFalse(exists("gas_stations", stationId));
        assertFalse(exists("vehicle_logs", vehicleLogId));
        assertFalse(exists("vehicles", vehicleId));

        jdbc.execute("DROP TABLE vehicle_logs");
        UUID vehicleWithoutLegacyTable = insertVehicle(userId, OLD);
        service.purge(CUTOFF, false, 100, 20);
        assertFalse(exists("vehicles", vehicleWithoutLegacyTable));
    }

    @Test
    void shouldHonorDryRunBatchSizeAndMaximumBatches() {
        UUID userId = insertUser();
        for (int index = 0; index < 5; index++) {
            insertAccount(userId, OLD - index);
        }

        PurgeRunResult dryRun = service.purge(CUTOFF, true, 2, 2);
        assertEquals(4L, dryRun.getCounts().get("accounts"));
        assertEquals(5, count("SELECT COUNT(*) FROM accounts"));

        PurgeRunResult realRun = service.purge(CUTOFF, false, 2, 2);
        assertEquals(4L, realRun.getCounts().get("accounts"));
        assertEquals(1, count("SELECT COUNT(*) FROM accounts"));
    }

    @Test
    void shouldRollbackTheWholeBatchWhenAnyDeleteFails() {
        UUID userId = insertUser();
        UUID categoryId = insertCategory(userId, null, OLD);
        UUID accountId = insertAccount(userId, OLD);
        jdbc.execute("""
                CREATE FUNCTION fail_account_delete() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced retention failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_account_delete
                BEFORE DELETE ON accounts
                FOR EACH STATEMENT EXECUTE FUNCTION fail_account_delete()
                """);

        assertThrows(RuntimeException.class, () -> service.purge(CUTOFF, false, 100, 20));

        assertTrue(exists("category", categoryId));
        assertTrue(exists("accounts", accountId));
    }

    @Test
    void shouldAllowOnlyOneAdvisoryLockHolder() throws Exception {
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            assertTrue(repository.tryAdvisoryLock(first));
            assertFalse(repository.tryAdvisoryLock(second));
            assertTrue(repository.releaseAdvisoryLock(first));
            assertTrue(repository.tryAdvisoryLock(second));
            assertTrue(repository.releaseAdvisoryLock(second));
        }
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (
                    id, username, email, password, role, enabled, account_non_expired,
                    account_non_locked, credentials_non_expired, oauth2user,
                    local_password_enabled, session_version, created_at
                ) VALUES (?, ?, ?, ?, ?, true, true, true, true, false, true, 0, ?)
                """, id, "user", id + "@test.local", "password", "USER", 1L);
        return id;
    }

    private UUID insertAccount(UUID userId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (
                    id, user_id, type, name, institution, currency, current_balance,
                    calculate_balance, initial_balance, is_default, enabled, created_at, deleted_at
                ) VALUES (?, ?, 'BANK', ?, 'Test', 'BRL', 0, true, 0, false, true, 1, ?)
                """, id, userId, "account-" + id, deletedAt);
        return id;
    }

    private UUID insertCategory(UUID userId, UUID parentId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO category (
                    id, user_id, sub_category_id, name, category_type, enabled,
                    is_sub_category, is_default, created_at, deleted_at
                ) VALUES (?, ?, ?, ?, 'DESPESA', true, ?, false, 1, ?)
                """, id, userId, parentId, "category-" + id, parentId != null, deletedAt);
        return id;
    }

    private UUID insertCreditCard(UUID userId, UUID accountId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO credit_cards (
                    id, user_id, account_id, name, total_limit, current_limit,
                    close_day, best_day, enabled, created_at, deleted_at
                ) VALUES (?, ?, ?, ?, 1000, 1000, 1, 10, true, 1, ?)
                """, id, userId, accountId, "card-" + id, deletedAt);
        return id;
    }

    private UUID insertRecurrenceRule(UUID userId, UUID accountId, UUID categoryId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO recurrence_rules (
                    id, user_id, account_id, category_id, name, base_amount, type,
                    frequency, start_date, status, created_at, deleted_at
                ) VALUES (?, ?, ?, ?, ?, 10, 'DESPESA', 'MONTHLY', 1, 'ACTIVE', 1, ?)
                """, id, userId, accountId, categoryId, "recurrence-" + id, deletedAt);
        return id;
    }

    private UUID insertTransaction(
            UUID userId,
            UUID accountId,
            UUID categoryId,
            UUID creditCardId,
            UUID parentId,
            UUID recurrenceId,
            UUID vehicleId,
            UUID gasStationId,
            Long deletedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO transactions (
                            id, user_id, account_id, category_id, credit_card_id,
                            parent_transaction_id, recurrence_rule_id, vehicle_id, gas_station_id,
                            date, name, type, amount, fixed, paid, enabled, full_tank,
                            created_at, deleted_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'DESPESA', 10, false,
                                  false, true, false, 1, ?)
                        """, id, userId, accountId, categoryId, creditCardId, parentId,
                recurrenceId, vehicleId, gasStationId, "transaction-" + id, deletedAt);
        return id;
    }

    private TransactionHierarchy insertTransactionHierarchy(
            Long parentDeletedAt,
            Long... childDeletedAtValues
    ) {
        UUID userId = insertUser();
        UUID accountId = insertAccount(userId, null);
        UUID categoryId = insertCategory(userId, null, null);
        UUID parentId = insertTransaction(
                userId,
                accountId,
                categoryId,
                null,
                null,
                null,
                null,
                null,
                parentDeletedAt
        );
        List<UUID> childIds = java.util.Arrays.stream(childDeletedAtValues)
                .map(deletedAt -> insertTransaction(
                        userId,
                        accountId,
                        categoryId,
                        null,
                        parentId,
                        null,
                        null,
                        null,
                        deletedAt
                ))
                .toList();
        return new TransactionHierarchy(parentId, childIds);
    }

    private UUID insertInvoice(UUID userId, UUID cardId, UUID transactionId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO invoicess (
                    id, user_id, credit_card_id, transaction_id, amount, month, year,
                    expiration_date, paid, enabled, created_at, deleted_at
                ) VALUES (?, ?, ?, ?, 10, 1, 2026, 1, false, true, 1, ?)
                """, id, userId, cardId, transactionId, deletedAt);
        return id;
    }

    private UUID insertInstallment(UUID userId, UUID invoiceId, UUID advancedInvoiceId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO installment_plan (
                            id, user_id, invoices_id, advanced_from_invoice_id, purchase_id,
                            date, name, type, amount, total_installments_plan, current_installment,
                            fixed, paid, enabled, created_at, deleted_at
                        ) VALUES (?, ?, ?, ?, ?, 1, ?, 'DESPESA', 10, 1, 1, false, false, true, 1, ?)
                        """, id, userId, invoiceId, advancedInvoiceId, UUID.randomUUID(),
                "installment-" + id, deletedAt);
        return id;
    }

    private UUID insertGasStation(UUID userId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO gas_stations (id, user_id, name, created_at, deleted_at) VALUES (?, ?, ?, 1, ?)",
                id,
                userId,
                "station-" + id,
                deletedAt
        );
        return id;
    }

    private UUID insertGasStationRanking(UUID stationId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO gas_station_rankings (id, gas_station_id, fuel_type) VALUES (?, ?, 'GASOLINA')",
                id,
                stationId
        );
        return id;
    }

    private UUID insertVehicle(UUID userId, Long deletedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO vehicles (
                    id, user_id, name, brand, model, year, current_odometer,
                    initial_odometer, created_at, deleted_at
                ) VALUES (?, ?, ?, 'Brand', 'Model', 2026, 0, 0, 1, ?)
                """, id, userId, "vehicle-" + id, deletedAt);
        return id;
    }

    private UUID insertAccountActionToken(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO account_action_tokens (
                    id, user_id, purpose, email_hash, ip_hash, code_digest,
                    expires_at, attempts, max_attempts, created_at, version
                ) VALUES (?, ?, 'PASSWORD_RESET', ?, ?, ?, 10, 0, 5, 1, 0)
                """, id, userId, "e".repeat(64), "i".repeat(64), "c".repeat(64));
        return id;
    }

    private UUID insertClosedTester() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO closed_test_testers (
                    id, email, normalized_email, enabled, created_at
                ) VALUES (?, ?, ?, true, 1)
                """, id, id + "@test.local", id + "@test.local");
        return id;
    }

    private void createVehicleLogsTable() {
        jdbc.execute("""
                CREATE TABLE vehicle_logs (
                    id uuid PRIMARY KEY,
                    vehicle_id uuid NOT NULL REFERENCES vehicles(id),
                    created_at bigint NOT NULL,
                    description varchar(255)
                )
                """);
    }

    private boolean exists(String table, UUID id) {
        Integer result = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?",
                Integer.class,
                id
        );
        return result != null && result == 1;
    }

    private BigDecimal activeTransactionTotal(UUID userId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions "
                        + "WHERE user_id = ? AND deleted_at IS NULL",
                BigDecimal.class,
                userId
        );
    }

    private BigDecimal accountBalance(UUID accountId) {
        return jdbc.queryForObject(
                "SELECT current_balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private record TransactionHierarchy(UUID parentId, List<UUID> childIds) {
    }
}
