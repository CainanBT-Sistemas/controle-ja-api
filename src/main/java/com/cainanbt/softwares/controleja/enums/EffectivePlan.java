package com.cainanbt.softwares.controleja.enums;

import com.cainanbt.softwares.controleja.services.entitlements.PlanLimits;

public enum EffectivePlan {
    FREE(false, false, true, false, false, new PlanLimits(3, 1, 0, 5, 2)),
    FREE_NO_ADS(false, false, false, false, true, new PlanLimits(3, 1, 0, 5, 2)),
    PLUS(false, true, false, true, true, new PlanLimits(10, 5, 2, 10, 10)),
    TESTER_FREE(true, false, true, false, false, new PlanLimits(3, 1, 0, 5, 2)),
    TESTER_PLUS(true, true, false, true, true, new PlanLimits(10, 5, 2, 10, 10));

    private final boolean tester;
    private final boolean plusEquivalent;
    private final boolean adsEnabled;
    private final boolean vehicleModule;
    private final boolean noAds;
    private final PlanLimits limits;

    EffectivePlan(
            boolean tester,
            boolean plusEquivalent,
            boolean adsEnabled,
            boolean vehicleModule,
            boolean noAds,
            PlanLimits limits
    ) {
        this.tester = tester;
        this.plusEquivalent = plusEquivalent;
        this.adsEnabled = adsEnabled;
        this.vehicleModule = vehicleModule;
        this.noAds = noAds;
        this.limits = limits;
    }

    public boolean isTester() {
        return tester;
    }

    public boolean isPlusEquivalent() {
        return plusEquivalent;
    }

    public boolean isAdsEnabled() {
        return adsEnabled;
    }

    public boolean hasVehicleModule() {
        return vehicleModule;
    }

    public boolean hasNoAds() {
        return noAds;
    }

    public PlanLimits getLimits() {
        return limits;
    }

    public EffectivePlan asPersistedBasePlan() {
        return switch (this) {
            case FREE_NO_ADS, PLUS -> this;
            default -> FREE;
        };
    }
}
