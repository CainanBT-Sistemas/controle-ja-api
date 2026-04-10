package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    List<InstallmentPlan> findByInvoicesId(UUID invoiceId);

    List<InstallmentPlan> findByPurchaseId(UUID purchaseId);

    @Query("SELECT i FROM InstallmentPlan i WHERE i.user.id = :userId AND i.date BETWEEN :start AND :end AND i.deletedAt IS NULL")
    List<InstallmentPlan> findByUserAndDateBetween(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);
}
