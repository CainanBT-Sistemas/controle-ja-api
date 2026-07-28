package com.cainanbt.softwares.controleja.services.users;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Removes all operational data owned by a user in FK-safe order.
 */
@Component
@RequiredArgsConstructor
public class UserOperationalDataResetter {

    private final EntityManager entityManager;

    public void deleteOperationalData(UUID userId) {
        breakCircularReferences(userId);
        deleteInvoiceItems(userId);
        deleteInvoices(userId);
        deleteTransactions(userId);
        deleteGasStationRankings(userId);
        deleteGasStations(userId);
        deleteRecurrenceRules(userId);
        deleteCreditCards(userId);
        deleteVehicleLogs(userId);
        deleteVehicles(userId);
        deleteCategories(userId);
        deleteAccounts(userId);
    }

    private void breakCircularReferences(UUID userId) {
        execute("""
                UPDATE transactions
                   SET parent_transaction_id = NULL,
                       target_invoice_id = NULL
                 WHERE user_id = CAST(:userId AS uuid)
                """, userId);
        execute("""
                UPDATE invoicess
                   SET transaction_id = NULL
                 WHERE user_id = CAST(:userId AS uuid)
                """, userId);
    }

    private void deleteInvoiceItems(UUID userId) {
        execute("DELETE FROM installment_plan WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteInvoices(UUID userId) {
        execute("DELETE FROM invoicess WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteTransactions(UUID userId) {
        execute("DELETE FROM transactions WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteGasStationRankings(UUID userId) {
        execute("""
                DELETE FROM gas_station_rankings ranking
                 USING gas_stations station
                 WHERE ranking.gas_station_id = station.id
                   AND station.user_id = CAST(:userId AS uuid)
                """, userId);
    }

    private void deleteGasStations(UUID userId) {
        execute("DELETE FROM gas_stations WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteRecurrenceRules(UUID userId) {
        execute("DELETE FROM recurrence_rules WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteCreditCards(UUID userId) {
        execute("DELETE FROM credit_cards WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteVehicleLogs(UUID userId) {
        if (!tableExists("vehicle_logs")) {
            return;
        }
        execute("""
                DELETE FROM vehicle_logs log
                 USING vehicles vehicle
                 WHERE log.vehicle_id = vehicle.id
                   AND vehicle.user_id = CAST(:userId AS uuid)
                """, userId);
    }

    private void deleteVehicles(UUID userId) {
        execute("DELETE FROM vehicles WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteCategories(UUID userId) {
        execute("""
                DELETE FROM category
                 WHERE user_id = CAST(:userId AS uuid)
                   AND sub_category_id IS NOT NULL
                """, userId);
        execute("DELETE FROM category WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void deleteAccounts(UUID userId) {
        execute("DELETE FROM accounts WHERE user_id = CAST(:userId AS uuid)", userId);
    }

    private void execute(String sql, UUID userId) {
        entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                          FROM information_schema.tables
                         WHERE table_schema = current_schema()
                           AND table_name = :tableName
                        """)
                .setParameter("tableName", tableName)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
