package com.cainanbt.softwares.controleja.services.billing.google;

import java.time.Instant;

public record PurchaseSnapshot(
        String productId,
        String basePlanId,
        String obfuscatedAccountId,
        String state,
        boolean active,
        boolean acknowledged,
        Instant startsAt,
        Instant expiresAt,
        Boolean autoRenew,
        String linkedPurchaseToken,
        boolean subscription,
        String orderId
) {
    @Override
    public String toString() {
        return "PurchaseSnapshot[productId=" + productId + ", state=" + state + "]";
    }
}
