package com.cainanbt.softwares.controleja.services.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.google-play")
public record BillingSettings(boolean enabled, String packageName, String tokenKey,
                              String pubsubAudience, String pubsubServiceAccount) {
    public static final String NO_ADS = "controleja_no_ads_lifetime";
    public static final String PLUS = "controleja_plus";

    public boolean allows(String candidatePackage, String product) {
        return packageName.equals(candidatePackage) && (NO_ADS.equals(product) || PLUS.equals(product));
    }

    public static boolean allowsBasePlan(String value) {
        return "monthly".equals(value) || "yearly".equals(value);
    }

    @Override
    public String toString() {
        return "BillingSettings[enabled=" + enabled + "]";
    }
}
