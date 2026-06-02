package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.InstallmentPlanRepository;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cainanbt.softwares.controleja.utils.ConstsMessages.PARCEL_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstallmentPlanServiceImpl implements InstallmentPlanService {

    private final InstallmentPlanRepository repository;

    /**
     * Persiste uma parcela ou item de fatura.
     */
    @Override
    @Transactional
    public InstallmentPlan save(InstallmentPlan installmentPlan) {
        return repository.save(installmentPlan);
    }

    /**
     * Persiste uma lista de parcelas em lote para reduzir round-trips ao banco.
     */
    @Override
    @Transactional
    public List<InstallmentPlan> saveAll(List<InstallmentPlan> installmentPlans) {
        return repository.saveAll(installmentPlans);
    }

    /**
     * Busca uma parcela por id sem impor regra de propriedade.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<InstallmentPlan> findById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Busca uma parcela por id e retorna 404 padronizado quando nao existir.
     */
    @Override
    @Transactional(readOnly = true)
    public InstallmentPlan findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, PARCEL_NOT_FOUND));
    }

    /**
     * Busca uma parcela por id garantindo que pertence ao usuario informado.
     */
    @Override
    @Transactional(readOnly = true)
    public InstallmentPlan findByIdAndUserIdOrThrow(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, PARCEL_NOT_FOUND));
    }

    /**
     * Busca todos os itens de uma fatura, inclusive itens removidos para auditoria historica.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findByInvoiceId(UUID invoiceId) {
        return repository.findByInvoicesId(invoiceId);
    }

    /**
     * Busca todos os itens de uma fatura filtrando pelo dono.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findByInvoiceIdAndUserId(UUID invoiceId, UUID userId) {
        return repository.findByInvoicesIdAndUserId(invoiceId, userId);
    }

    /**
     * Busca itens ativos de uma fatura ordenados por data.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findActiveByInvoiceIdOrderByDate(UUID invoiceId) {
        return repository.findActiveByInvoiceIdOrderByDate(invoiceId);
    }

    /**
     * Busca todas as parcelas de uma compra, inclusive removidas quando o historico for necessario.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findByPurchaseId(UUID purchaseId) {
        return repository.findByPurchaseId(purchaseId);
    }

    /**
     * Busca todas as parcelas de uma compra filtrando pelo dono.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findByPurchaseIdAndUserId(UUID purchaseId, UUID userId) {
        return repository.findByPurchaseIdAndUserId(purchaseId, userId);
    }

    /**
     * Busca parcelas ativas de uma compra.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findActiveByPurchaseId(UUID purchaseId) {
        return repository.findActiveByPurchaseId(purchaseId);
    }

    /**
     * Busca parcelas ativas de uma compra filtrando pelo dono.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findActiveByPurchaseIdAndUserId(UUID purchaseId, UUID userId) {
        return repository.findActiveByPurchaseIdAndUserId(purchaseId, userId);
    }

    /**
     * Busca parcelas de um usuario dentro de um periodo.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findByUserAndDateBetween(UUID userId, Long start, Long end) {
        return repository.findByUserAndDateBetween(userId, start, end);
    }

    /**
     * Busca parcelas futuras elegiveis para adiantamento.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findAdvanceableByInvoiceIds(List<UUID> invoiceIds) {
        return repository.findAdvanceableByInvoiceIds(invoiceIds);
    }

    /**
     * Busca parcelas futuras elegiveis para adiantamento filtrando pelo dono.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InstallmentPlan> findAdvanceableByInvoiceIdsAndUserId(List<UUID> invoiceIds, UUID userId) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        return repository.findAdvanceableByInvoiceIdsAndUserId(invoiceIds, userId);
    }
}
