package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    /**
     * Busca uma parcela especifica garantindo que ela pertence ao usuario.
     */
    Optional<InstallmentPlan> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Busca todos os itens vinculados a uma fatura, preservando histórico de itens removidos.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.invoices.id = :invoiceId")
    List<InstallmentPlan> findByInvoicesId(@Param("invoiceId") UUID invoiceId);

    /**
     * Busca todos os itens vinculados a uma fatura do usuario autenticado.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.invoices.id = :invoiceId AND p.user.id = :userId")
    List<InstallmentPlan> findByInvoicesIdAndUserId(@Param("invoiceId") UUID invoiceId, @Param("userId") UUID userId);

    /**
     * Busca apenas itens ativos de uma fatura ordenados por data.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.invoices.id = :invoiceId AND p.deletedAt IS NULL ORDER BY p.date ASC")
    List<InstallmentPlan> findActiveByInvoiceIdOrderByDate(@Param("invoiceId") UUID invoiceId);

    /**
     * Busca todas as parcelas de uma compra pela transação pai.
     */
    List<InstallmentPlan> findByPurchaseId(UUID purchaseId);

    /**
     * Busca todas as parcelas de uma compra de um usuario especifico.
     */
    List<InstallmentPlan> findByPurchaseIdAndUserId(UUID purchaseId, UUID userId);

    /**
     * Busca apenas parcelas ativas de uma compra pela transação pai.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.purchaseId = :purchaseId AND p.deletedAt IS NULL")
    List<InstallmentPlan> findActiveByPurchaseId(@Param("purchaseId") UUID purchaseId);

    /**
     * Busca apenas parcelas ativas de uma compra do usuario autenticado.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.purchaseId = :purchaseId AND p.user.id = :userId AND p.deletedAt IS NULL")
    List<InstallmentPlan> findActiveByPurchaseIdAndUserId(@Param("purchaseId") UUID purchaseId, @Param("userId") UUID userId);

    /**
     * Busca parcelas do usuário por período para relatórios.
     */
    @Query("SELECT i FROM InstallmentPlan i WHERE i.user.id = :userId AND i.date BETWEEN :start AND :end AND i.deletedAt IS NULL")
    List<InstallmentPlan> findByUserAndDateBetween(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);

    /**
     * Busca parcelas futuras pendentes e positivas que podem ser adiantadas.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.invoices.id IN :invoiceIds AND p.deletedAt IS NULL AND p.paid = false AND p.amount > 0")
    List<InstallmentPlan> findAdvanceableByInvoiceIds(@Param("invoiceIds") List<UUID> invoiceIds);

    /**
     * Busca parcelas futuras pendentes e positivas do usuario autenticado para adiantamento.
     */
    @Query("SELECT p FROM InstallmentPlan p WHERE p.invoices.id IN :invoiceIds AND p.user.id = :userId AND p.deletedAt IS NULL AND p.paid = false AND p.amount > 0")
    List<InstallmentPlan> findAdvanceableByInvoiceIdsAndUserId(@Param("invoiceIds") List<UUID> invoiceIds, @Param("userId") UUID userId);
}
