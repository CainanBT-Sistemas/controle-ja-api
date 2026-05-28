package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.InstallmentPlanRepository;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cainanbt.softwares.controleja.utils.ConstsMessages.PARCEL_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class InstallmentPlanServiceImpl implements InstallmentPlanService {

    private final InstallmentPlanRepository repository;

    @Override
    public InstallmentPlan save(InstallmentPlan installmentPlan) {
        return repository.save(installmentPlan);
    }

    @Override
    public List<InstallmentPlan> saveAll(List<InstallmentPlan> installmentPlans) {
        return repository.saveAll(installmentPlans);
    }

    @Override
    public Optional<InstallmentPlan> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public InstallmentPlan findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, PARCEL_NOT_FOUND));
    }

    @Override
    public List<InstallmentPlan> findByInvoiceId(UUID invoiceId) {
        return repository.findByInvoicesId(invoiceId);
    }

    @Override
    public List<InstallmentPlan> findByPurchaseId(UUID purchaseId) {
        return repository.findByPurchaseId(purchaseId);
    }

    @Override
    public List<InstallmentPlan> findByUserAndDateBetween(UUID userId, Long start, Long end) {
        return repository.findByUserAndDateBetween(userId, start, end);
    }

    @Override
    public List<InstallmentPlan> findAdvanceableByInvoiceIds(List<UUID> invoiceIds) {
        return repository.findAdvanceableByInvoiceIds(invoiceIds);
    }
}