package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, UUID> {
    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId " +
            "AND t.account.type != com.cainanbt.softwares.controleja.enums.AccountType.CREDIT_CARD " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date DESC, t.createdAt DESC")
    List<Transactions> findCashFlowTransactionsByMonth(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId AND t.deletedAt IS NULL ORDER BY t.date DESC")
    List<Transactions> findByUserIdOrderByDateDesc(@Param("userId") UUID userId);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(COALESCE(parentCategory.name, c.name), SUM(t.amount), COALESCE(parentCategory.color, c.color)) " +
            "FROM Transactions t JOIN t.category c LEFT JOIN c.subCategory parentCategory " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.account.type != com.cainanbt.softwares.controleja.enums.AccountType.CREDIT_CARD " +
            "AND t.paid = true AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "GROUP BY COALESCE(parentCategory.name, c.name), COALESCE(parentCategory.color, c.color) ORDER BY SUM(t.amount) DESC")
    List<ChartDataDTO> getGeneralExpensesByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("type") TransactionType type);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(COALESCE(parentCategory.name, c.name), SUM(i.amount), COALESCE(parentCategory.color, c.color)) " +
            "FROM InstallmentPlan i, Transactions t JOIN t.category c LEFT JOIN c.subCategory parentCategory " +
            "WHERE i.purchaseId = t.id AND i.user.id = :userId " +
            "AND t.type = :type " +
            "AND i.date BETWEEN :start AND :end " +
            "AND i.deletedAt IS NULL AND t.deletedAt IS NULL " +
            "GROUP BY COALESCE(parentCategory.name, c.name), COALESCE(parentCategory.color, c.color) ORDER BY SUM(i.amount) DESC")
    List<ChartDataDTO> getCreditCardExpensesByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("type") TransactionType type);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(COALESCE(parentCategory.name, c.name), SUM(t.amount), COALESCE(parentCategory.color, c.color)) " +
            "FROM Transactions t JOIN t.category c LEFT JOIN c.subCategory parentCategory " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.paid = true AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "GROUP BY COALESCE(parentCategory.name, c.name), COALESCE(parentCategory.color, c.color) ORDER BY SUM(t.amount) DESC")
    List<ChartDataDTO> getExpensesByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("type") TransactionType type);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(CAST(t.fuelType AS string), SUM(t.amount), '#FF9800') " +
            "FROM Transactions t WHERE t.user.id = :userId AND t.type = 'DESPESA' AND t.vehicle IS NOT NULL " +
            "AND t.fuelType IS NOT NULL AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "GROUP BY t.fuelType ORDER BY SUM(t.amount) DESC")
    List<ChartDataDTO> getExpensesByFuelType(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT SUM(t.amount) FROM Transactions t " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL")
    BigDecimal getTotalByType(@Param("userId") UUID userId, @Param("type") TransactionType type, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(CAST(t.date AS string), t.amount, '#00E676') " +
            "FROM Transactions t WHERE t.user.id = :userId AND t.type = 'DESPESA' AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date ASC")
    List<ChartDataDTO> getEvolutionRawDataAll(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT new com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO(CAST(t.date AS string), t.amount, '#00E676') " +
            "FROM Transactions t WHERE t.user.id = :userId AND t.type = 'DESPESA' AND t.category.id = :categoryId " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date ASC")
    List<ChartDataDTO> getEvolutionRawDataByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("categoryId") UUID categoryId);

    @Query("SELECT t FROM Transactions t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Transactions> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query(value = "SELECT * FROM transactions WHERE id = :id", nativeQuery = true)
    Optional<Transactions> findByIdIncludingDeleted(@Param("id") UUID id);

    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date DESC, t.createdAt DESC")
    List<Transactions> findTransactionsByMonth(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT MAX(t.date) FROM Transactions t WHERE t.recurrenceRule.id = :ruleId AND t.deletedAt IS NULL")
    Long findMaxDateByRuleId(@Param("ruleId") UUID ruleId);

    @Query("SELECT t FROM Transactions t WHERE t.recurrenceRule.id = :ruleId AND t.paid = false AND t.date >= :now AND t.deletedAt IS NULL")
    List<Transactions> findFutureUnpaidByRuleId(@Param("ruleId") UUID ruleId, @Param("now") Long now);

    @Query("SELECT t FROM Transactions t WHERE t.parentTransaction.id = :parentId AND t.deletedAt IS NULL")
    List<Transactions> findByParentTransactionId(@Param("parentId") UUID parentId);

    @Query("SELECT t FROM Transactions t WHERE t.parentTransaction.id = :parentId " +
            "AND t.type = com.cainanbt.softwares.controleja.enums.TransactionType.TRANSFERENCIA_ENTRADA " +
            "AND t.deletedAt IS NULL")
    Optional<Transactions> findTransferChildByParentId(@Param("parentId") UUID parentId);

    @Modifying
    @Query("UPDATE Transactions t SET t.deletedAt = :dateNow WHERE t.parentTransaction.id = :parentId AND t.deletedAt IS NULL")
    void deleteByParentId(@Param("parentId") UUID parentId, @Param("dateNow") long dateNow);

    List<Transactions> findTop3ByUserIdAndTypeAndPaidFalseAndDeletedAtIsNullOrderByDateAsc(UUID userId, TransactionType type);

    @Query("SELECT COUNT(t) FROM Transactions t WHERE t.category.id = :categoryId AND t.deletedAt IS NULL")
    long countByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId AND t.type = :type AND t.paid = false AND t.date <= :endDate AND t.account.type != com.cainanbt.softwares.controleja.enums.AccountType.CREDIT_CARD AND t.deletedAt IS NULL ORDER BY t.date ASC")
    List<Transactions> findPendingUpToDate(@Param("userId") UUID userId, @Param("type") TransactionType type, @Param("endDate") Long endDate);

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN t.type = com.cainanbt.softwares.controleja.enums.TransactionType.DESPESA THEN t.amount " +
            "WHEN t.type = com.cainanbt.softwares.controleja.enums.TransactionType.RECEITA THEN -t.amount " +
            "ELSE 0 END), 0) " +
            "FROM Transactions t LEFT JOIN t.category.subCategory parentCategory " +
            "WHERE t.vehicle.id = :vehicleId " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "AND t.type IN (com.cainanbt.softwares.controleja.enums.TransactionType.DESPESA, com.cainanbt.softwares.controleja.enums.TransactionType.RECEITA) " +
            "AND (LOWER(t.category.name) IN ('veículo', 'veículos') " +
            "OR LOWER(parentCategory.name) IN ('veículo', 'veículos'))")
    BigDecimal getNetVehicleCost(@Param("vehicleId") UUID vehicleId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT t FROM Transactions t LEFT JOIN t.category.subCategory parentCategory " +
            "WHERE t.vehicle.id = :vehicleId AND t.type = 'DESPESA' " +
            "AND t.liters IS NOT NULL AND t.liters > 0 " +
            "AND t.amount IS NOT NULL AND t.amount > 0 " +
            "AND t.currentOdometer IS NOT NULL AND t.currentOdometer > 0 " +
            "AND (LOWER(t.category.name) = 'abastecimento' " +
            "OR LOWER(parentCategory.name) = 'abastecimento' " +
            "OR t.fuelType IS NOT NULL OR t.gasStation IS NOT NULL) " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date ASC, t.createdAt ASC")
    List<Transactions> findRefuelsByVehicleAndDateBetween(@Param("vehicleId") UUID vehicleId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT t FROM Transactions t LEFT JOIN t.category.subCategory parentCategory " +
            "WHERE t.vehicle.id = :vehicleId AND t.type = 'DESPESA' " +
            "AND t.currentOdometer IS NOT NULL AND t.currentOdometer > 0 " +
            "AND (LOWER(t.category.name) = 'abastecimento' " +
            "OR LOWER(parentCategory.name) = 'abastecimento' " +
            "OR t.liters IS NOT NULL OR t.fuelType IS NOT NULL OR t.gasStation IS NOT NULL) " +
            "AND t.date < :date AND t.deletedAt IS NULL " +
            "ORDER BY t.date DESC, t.createdAt DESC")
    List<Transactions> findPreviousValidRefuelsByVehicleBeforeDate(@Param("vehicleId") UUID vehicleId, @Param("date") Long date);

    @Query("SELECT t FROM Transactions t WHERE t.vehicle.id = :vehicleId " +
            "AND t.id <> :transactionId AND t.currentOdometer IS NOT NULL AND t.currentOdometer > 0 " +
            "AND t.deletedAt IS NULL " +
            "AND (t.date < :date OR (t.date = :date AND COALESCE(t.createdAt, 0) < :createdAt)) " +
            "ORDER BY t.date DESC, t.createdAt DESC")
    List<Transactions> findPreviousOdometerTransactions(
            @Param("vehicleId") UUID vehicleId,
            @Param("transactionId") UUID transactionId,
            @Param("date") Long date,
            @Param("createdAt") Long createdAt);

    @Query("SELECT t FROM Transactions t WHERE t.vehicle.id = :vehicleId " +
            "AND t.id <> :transactionId AND t.currentOdometer IS NOT NULL AND t.currentOdometer > 0 " +
            "AND t.deletedAt IS NULL " +
            "AND (t.date > :date OR (t.date = :date AND COALESCE(t.createdAt, 0) > :createdAt)) " +
            "ORDER BY t.date ASC, t.createdAt ASC")
    List<Transactions> findNextOdometerTransactions(
            @Param("vehicleId") UUID vehicleId,
            @Param("transactionId") UUID transactionId,
            @Param("date") Long date,
            @Param("createdAt") Long createdAt);

    @Query("SELECT MAX(t.currentOdometer) FROM Transactions t WHERE t.vehicle.id = :vehicleId " +
            "AND t.currentOdometer IS NOT NULL AND t.currentOdometer > 0 AND t.deletedAt IS NULL")
    BigDecimal findMaxCurrentOdometerByVehicleId(@Param("vehicleId") UUID vehicleId);

    @Query("SELECT t FROM Transactions t WHERE t.vehicle.id = :vehicleId AND t.type = 'DESPESA' " +
            "AND t.fuelType IS NOT NULL AND t.liters IS NOT NULL AND t.liters > 0 " +
            "AND t.currentOdometer IS NOT NULL AND t.date <= :date AND t.deletedAt IS NULL " +
            "ORDER BY t.date DESC, t.createdAt DESC")
    List<Transactions> findValidRefuelsByVehicleUpToDate(@Param("vehicleId") UUID vehicleId, @Param("date") Long date);
}
