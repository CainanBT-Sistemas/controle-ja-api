package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentPlanService {
    /**
     * Persiste uma parcela ou item de fatura.
     */
    InstallmentPlan save(InstallmentPlan installmentPlan);

    /**
     * Persiste parcelas em lote para reduzir chamadas ao banco em operações de fatura.
     */
    List<InstallmentPlan> saveAll(List<InstallmentPlan> installmentPlans);

    /**
     * Busca uma parcela pelo id.
     */
    Optional<InstallmentPlan> findById(UUID id);

    /**
     * Busca uma parcela pelo id e falha com erro de domínio quando não existe.
     */
    InstallmentPlan findByIdOrThrow(UUID id);

    /**
     * Busca uma parcela pelo id garantindo que ela pertence ao usuario informado.
     */
    InstallmentPlan findByIdAndUserIdOrThrow(UUID id, UUID userId);

    /**
     * Busca todos os itens de uma fatura, incluindo itens cancelados quando o fluxo precisar de histórico.
     */
    List<InstallmentPlan> findByInvoiceId(UUID invoiceId);

    /**
     * Busca todos os itens de uma fatura garantindo propriedade pelo usuario.
     */
    List<InstallmentPlan> findByInvoiceIdAndUserId(UUID invoiceId, UUID userId);

    /**
     * Busca itens ativos de uma fatura já ordenados por data para telas e cálculos atuais.
     */
    List<InstallmentPlan> findActiveByInvoiceIdOrderByDate(UUID invoiceId);

    /**
     * Busca todas as parcelas de uma compra pelo id da transação pai.
     */
    List<InstallmentPlan> findByPurchaseId(UUID purchaseId);

    /**
     * Busca todas as parcelas de uma compra garantindo propriedade pelo usuario.
     */
    List<InstallmentPlan> findByPurchaseIdAndUserId(UUID purchaseId, UUID userId);

    /**
     * Busca somente parcelas ativas de uma compra pelo id da transação pai.
     */
    List<InstallmentPlan> findActiveByPurchaseId(UUID purchaseId);

    /**
     * Busca somente parcelas ativas de uma compra garantindo propriedade pelo usuario.
     */
    List<InstallmentPlan> findActiveByPurchaseIdAndUserId(UUID purchaseId, UUID userId);

    /**
     * Busca parcelas do usuário em um período.
     */
    List<InstallmentPlan> findByUserAndDateBetween(UUID userId, Long start, Long end);

    /**
     * Busca parcelas de fatura que devem compor o custo mensal de veículos.
     */
    List<InstallmentPlan> findVehicleInstallmentsByUserAndDateBetween(UUID userId, Long start, Long end);

    /**
     * Busca parcelas futuras elegíveis para adiantamento em lote de faturas.
     */
    List<InstallmentPlan> findAdvanceableByInvoiceIds(List<UUID> invoiceIds);

    /**
     * Busca parcelas futuras elegíveis para adiantamento garantindo propriedade pelo usuario.
     */
    List<InstallmentPlan> findAdvanceableByInvoiceIdsAndUserId(List<UUID> invoiceIds, UUID userId);
}
