package com.cainanbt.softwares.controleja.services.entitlements;

public record PlanLimits(
        int accounts,
        int creditCards,
        int vehicles,
        int categories,
        int subcategoriesPerParent
) {
}
