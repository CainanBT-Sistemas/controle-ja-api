package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, UUID> {

    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId AND t.deletedAt IS NULL ORDER BY t.date DESC")
    List<Transactions> findByUserIdOrderByDateDesc(@Param("userId") UUID userId);

    @Query("SELECT c.name AS label, SUM(t.amount) AS value " +
            "FROM Transactions t JOIN t.category c " +
            "WHERE t.user.id = :userId " +
            "AND t.type = :type " +
            "AND t.date BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL " +
            "GROUP BY c.name " +
            "ORDER BY value DESC")
    List<ChartDataDTO> getExpensesByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("type") TransactionType type);

    @Query("SELECT CAST(t.fuelType AS string) AS label, SUM(t.amount) AS value " +
            "FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = 'DESPESA' " +
            "AND t.vehicle IS NOT NULL " +
            "AND t.fuelType IS NOT NULL " +
            "AND t.date BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL " +
            "GROUP BY t.fuelType " +
            "ORDER BY value DESC")
    List<ChartDataDTO> getExpensesByFuelType(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT SUM(t.amount) FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = :type " +
            "AND t.date BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL")
    BigDecimal getTotalByType(@Param("userId") UUID userId, @Param("type") TransactionType type, @Param("start") Long start, @Param("end") Long end);

    // CONSULTAS SEPARADAS PARA EVITAR O ERRO $2 DO POSTGRES
    @Query("SELECT CAST(t.date AS string) AS label, t.amount AS value " +
            "FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = 'DESPESA' " +
            "AND t.date BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL " +
            "ORDER BY t.date ASC")
    List<ChartDataDTO> getEvolutionRawDataAll(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT CAST(t.date AS string) AS label, t.amount AS value " +
            "FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = 'DESPESA' " +
            "AND t.category.id = :categoryId " +
            "AND t.date BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL " +
            "ORDER BY t.date ASC")
    List<ChartDataDTO> getEvolutionRawDataByCategory(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end, @Param("categoryId") UUID categoryId);

    @Query("SELECT t FROM Transactions t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Transactions> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT t FROM Transactions t WHERE t.user.id = :userId " +
            "AND t.date BETWEEN :start AND :end AND t.deletedAt IS NULL " +
            "ORDER BY t.date DESC")
    List<Transactions> findTransactionsByMonth(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    @Query("SELECT MAX(t.date) FROM Transactions t WHERE t.recurrenceRule.id = :ruleId AND t.deletedAt IS NULL")
    Long findMaxDateByRuleId(@Param("ruleId") UUID ruleId);

    @Query("SELECT t FROM Transactions t WHERE t.recurrenceRule.id = :ruleId AND t.paid = false AND t.date >= :now AND t.deletedAt IS NULL")
    List<Transactions> findFutureUnpaidByRuleId(@Param("ruleId") UUID ruleId, @Param("now") Long now);

    @Query("SELECT t FROM Transactions t WHERE t.parentTransaction.id = :parentId AND t.deletedAt IS NULL")
    List<Transactions> findByParentTransactionId(@Param("parentId") UUID parentId);

}