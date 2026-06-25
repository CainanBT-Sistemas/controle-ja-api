package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementServiceImpTest {

    @Test
    void shouldReturnTesterEntitlementForAllowedEmail() {
        EntitlementServiceImp service = new EntitlementServiceImp(
                new ClosedTestAccessPolicyImp(false, " tester@controleja.local ")
        );
        Users user = Users.builder().email("TESTER@controleja.local").build();

        UserEntitlementsDTO entitlements = service.buildForUser(user);

        assertTrue(entitlements.getTester());
        assertTrue(entitlements.getPlusEquivalent());
        assertTrue(entitlements.getPermissions().getVehicleModule());
        assertTrue(entitlements.getPermissions().getExpandedLimits());
        assertTrue(entitlements.getPermissions().getNoAds());
        assertFalse(entitlements.getAdsEnabled());
    }

    @Test
    void shouldReturnFreeEntitlementForOrdinaryUser() {
        EntitlementServiceImp service = new EntitlementServiceImp(
                new ClosedTestAccessPolicyImp(false, "tester@controleja.local")
        );
        Users user = Users.builder().email("common@controleja.local").build();

        UserEntitlementsDTO entitlements = service.buildForUser(user);

        assertFalse(entitlements.getTester());
        assertFalse(entitlements.getPlusEquivalent());
        assertFalse(entitlements.getPermissions().getVehicleModule());
        assertFalse(entitlements.getPermissions().getExpandedLimits());
        assertFalse(entitlements.getPermissions().getNoAds());
        assertTrue(entitlements.getAdsEnabled());
    }
}
