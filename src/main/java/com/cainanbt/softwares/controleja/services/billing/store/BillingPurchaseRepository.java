package com.cainanbt.softwares.controleja.services.billing.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPurchaseRepository extends JpaRepository<BillingPurchase, UUID> {
    Optional<BillingPurchase> findByFingerprint(String fingerprint);

    List<BillingPurchase> findByUserIdAndActiveTrueAndSupersededFalse(UUID userId);
}
