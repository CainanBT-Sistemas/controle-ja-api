package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, UUID> {
    List<Transactions> findByUserIdOrderByDateDesc(UUID userId);

    @Query("SELECT c.name AS label, SUM(t.amount) AS value " +
            "FROM Transactions t JOIN t.category c " +
            "WHERE t.user.id = :userId " +
            "AND t.type = :type " +
            "AND t.date BETWEEN :start AND :end " +
            "GROUP BY c.name " +
            "ORDER BY value DESC")
    List<ChartDataDTO> getExpensesByCategory(UUID userId, Long start, Long end, TransactionType type);

    @Query("SELECT CAST(t.fuelType AS string) AS label, SUM(t.amount) AS value " +
            "FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = 'DESPESA' " +
            "AND t.vehicle IS NOT NULL " +
            "AND t.fuelType IS NOT NULL " +
            "AND t.date BETWEEN :start AND :end " +
            "GROUP BY t.fuelType " +
            "ORDER BY value DESC")
    List<ChartDataDTO> getExpensesByFuelType(UUID userId, Long start, Long end);

    @Query("SELECT SUM(t.amount) FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = :type " +
            "AND t.date BETWEEN :start AND :end")
    BigDecimal getTotalByType(UUID userId, TransactionType type, Long start, Long end);

    @Query("SELECT CAST(t.date AS string) AS label, t.amount AS value " +
            "FROM Transactions t " +
            "WHERE t.user.id = :userId " +
            "AND t.type = 'DESPESA' " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND t.date BETWEEN :start AND :end " +
            "ORDER BY t.date ASC")
    List<ChartDataDTO> getEvolutionRawData(UUID userId, Long start, Long end, UUID categoryId);
}
