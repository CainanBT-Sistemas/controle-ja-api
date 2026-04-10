package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentPlanService {
    InstallmentPlan save(InstallmentPlan installmentPlan);

    List<InstallmentPlan> saveAll(List<InstallmentPlan> installmentPlans);

    Optional<InstallmentPlan> findById(UUID id);

    InstallmentPlan findByIdOrThrow(UUID id);

    List<InstallmentPlan> findByInvoiceId(UUID invoiceId);

    List<InstallmentPlan> findByPurchaseId(UUID purchaseId);

    List<InstallmentPlan> findByUserAndDateBetween(UUID userId, Long start, Long end);
}