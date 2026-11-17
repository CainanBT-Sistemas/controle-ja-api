package com.cainanbt.softwares.controleja.services.billing;

import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.services.billing.store.BillingPurchase;
import com.cainanbt.softwares.controleja.services.billing.store.BillingPurchaseRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BillingRights {
    private final BillingSettings settings;
    private final BillingPurchaseRepository purchases;

    public BillingRights(BillingSettings settings, BillingPurchaseRepository purchases) {
        this.settings = settings;
        this.purchases = purchases;
    }

    public EffectivePlan resolve(UUID userId) {
        if (!settings.enabled() || userId == null) return EffectivePlan.FREE;
        Instant now = Instant.now();
        EffectivePlan result = EffectivePlan.FREE;
        for (BillingPurchase purchase : purchases.findByUserIdAndActiveTrueAndSupersededFalse(userId)) {
            if (purchase.getStartsAt() != null && purchase.getStartsAt().isAfter(now)) continue;
            if (purchase.isSubscription() && BillingSettings.PLUS.equals(purchase.getProductId())
                    && purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(now)) {
                return EffectivePlan.PLUS;
            }
            if (!purchase.isSubscription() && BillingSettings.NO_ADS.equals(purchase.getProductId())) {
                result = EffectivePlan.FREE_NO_ADS;
            }
        }
        return result;
    }
}
