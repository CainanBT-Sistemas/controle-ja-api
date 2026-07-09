package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Invoices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoicesRepository extends JpaRepository<Invoices, UUID> {
    /**
     * Busca a fatura de um cartão pelo ciclo de mês e ano.
     */
    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);

    /**
     * Busca faturas do usuário em um intervalo de vencimento.
     */
    @Query("SELECT i FROM Invoices i WHERE i.user.id = :userId AND i.expirationDate BETWEEN :start AND :end AND i.deletedAt IS NULL")
    List<Invoices> findByUserAndDateBetween(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    /**
     * Busca as três próximas faturas pendentes para alertas rápidos.
     */
    List<Invoices> findTop3ByUserIdAndPaidFalseAndDeletedAtIsNullOrderByExpirationDateAsc(UUID userId);

    /**
     * Busca faturas pendentes vencidas ou a vencer até uma data.
     */
    @Query("SELECT i FROM Invoices i WHERE i.user.id = :userId AND i.paid = false AND i.amount > 0 AND i.expirationDate <= :endDate AND i.deletedAt IS NULL ORDER BY i.expirationDate ASC")
    List<Invoices> findPendingInvoicesUpToDate(@Param("userId") UUID userId, @Param("endDate") Long endDate);

    /**
     * Busca faturas futuras pendentes do cartão para localizar parcelas adiantáveis.
     */
    @Query("SELECT i FROM Invoices i WHERE i.user.id = :userId AND i.creditCard.id = :cardId AND i.expirationDate > :expirationDate AND i.paid = false AND i.enabled = true AND i.amount > 0 AND i.deletedAt IS NULL")
    List<Invoices> findFutureUnpaidByCardAndDate(@Param("userId") UUID userId, @Param("cardId") UUID cardId, @Param("expirationDate") Long expirationDate);

    /**
     * Impede remover cartao enquanto houver fatura ativa ou historica vinculada.
     */
    @Query("SELECT COUNT(i) > 0 FROM Invoices i WHERE i.creditCard.id = :cardId AND i.user.id = :userId AND i.deletedAt IS NULL")
    boolean existsActiveByCreditCardIdAndUserId(@Param("cardId") UUID cardId, @Param("userId") UUID userId);
}
