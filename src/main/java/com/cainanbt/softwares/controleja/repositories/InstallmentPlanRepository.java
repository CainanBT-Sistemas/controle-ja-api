package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    List<InstallmentPlan> findByInvoicesId(UUID invoiceId);
}
