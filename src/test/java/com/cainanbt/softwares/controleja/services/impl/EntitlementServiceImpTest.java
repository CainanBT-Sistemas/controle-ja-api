package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.ClosedTestTester;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.repositories.ClosedTestTesterRepository;
import com.cainanbt.softwares.controleja.services.entitlements.EntitlementReconciliationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementServiceImpTest {

    @Test
    void shouldReturnTesterPlusEntitlementForConfiguredTester() {
        ClosedTestTesterRepository repository = mock(ClosedTestTesterRepository.class);
        EntitlementReconciliationService reconciliationService = mock(EntitlementReconciliationService.class);
        when(repository.findByNormalizedEmailAndEnabledTrue("tester@controleja.local"))
                .thenReturn(Optional.of(ClosedTestTester.builder()
                        .profile(EffectivePlan.TESTER_PLUS)
                        .enabled(true)
                        .build()));
        EntitlementServiceImp service = new EntitlementServiceImp(repository, reconciliationService);
        Users user = Users.builder()
                .email("TESTER@controleja.local")
                .plan(EffectivePlan.FREE)
                .build();

        UserEntitlementsDTO entitlements = service.buildForUser(user);

        assertEquals("TESTER_PLUS", entitlements.getPlan());
        assertTrue(entitlements.getTester());
        assertTrue(entitlements.getPlusEquivalent());
        assertTrue(entitlements.getPermissions().getVehicleModule());
        assertTrue(entitlements.getPermissions().getNoAds());
        assertFalse(entitlements.getAdsEnabled());
        assertEquals(10, entitlements.getLimits().getAccounts());
        assertEquals(5, entitlements.getLimits().getCreditCards());
        assertEquals(2, entitlements.getLimits().getVehicles());
        assertEquals(10, entitlements.getLimits().getCategories());
        assertEquals(10, entitlements.getLimits().getSubcategoriesPerParent());
        verify(reconciliationService).reconcile(user, EffectivePlan.TESTER_PLUS);
    }

    @Test
    void shouldReturnTesterFreeEntitlementForConfiguredTester() {
        ClosedTestTesterRepository repository = mock(ClosedTestTesterRepository.class);
        when(repository.findByNormalizedEmailAndEnabledTrue("tester@controleja.local"))
                .thenReturn(Optional.of(ClosedTestTester.builder()
                        .profile(EffectivePlan.TESTER_FREE)
                        .enabled(true)
                        .build()));
        EntitlementServiceImp service = new EntitlementServiceImp(
                repository,
                mock(EntitlementReconciliationService.class)
        );
        Users user = Users.builder()
                .email("tester@controleja.local")
                .plan(EffectivePlan.PLUS)
                .build();

        UserEntitlementsDTO entitlements = service.buildForUser(user);

        assertEquals("TESTER_FREE", entitlements.getPlan());
        assertTrue(entitlements.getTester());
        assertFalse(entitlements.getPlusEquivalent());
        assertTrue(entitlements.getAdsEnabled());
        assertEquals(3, entitlements.getLimits().getAccounts());
        assertEquals(0, entitlements.getLimits().getVehicles());
    }

    @Test
    void shouldReturnFreeEntitlementForOrdinaryUser() {
        UserEntitlementsDTO entitlements = buildFor(EffectivePlan.FREE);

        assertEquals("FREE", entitlements.getPlan());
        assertFalse(entitlements.getTester());
        assertFalse(entitlements.getPlusEquivalent());
        assertFalse(entitlements.getPermissions().getVehicleModule());
        assertFalse(entitlements.getPermissions().getNoAds());
        assertTrue(entitlements.getAdsEnabled());
        assertEquals(3, entitlements.getLimits().getAccounts());
        assertEquals(1, entitlements.getLimits().getCreditCards());
        assertEquals(0, entitlements.getLimits().getVehicles());
        assertEquals(5, entitlements.getLimits().getCategories());
        assertEquals(2, entitlements.getLimits().getSubcategoriesPerParent());
    }

    @Test
    void shouldReturnFreeNoAdsEntitlementForPersistedRight() {
        UserEntitlementsDTO entitlements = buildFor(EffectivePlan.FREE_NO_ADS);

        assertEquals("FREE_NO_ADS", entitlements.getPlan());
        assertFalse(entitlements.getTester());
        assertFalse(entitlements.getPlusEquivalent());
        assertFalse(entitlements.getAdsEnabled());
        assertTrue(entitlements.getPermissions().getNoAds());
        assertEquals(3, entitlements.getLimits().getAccounts());
    }

    @Test
    void shouldReturnPlusEntitlementForPersistedRight() {
        UserEntitlementsDTO entitlements = buildFor(EffectivePlan.PLUS);

        assertEquals("PLUS", entitlements.getPlan());
        assertFalse(entitlements.getTester());
        assertTrue(entitlements.getPlusEquivalent());
        assertFalse(entitlements.getAdsEnabled());
        assertTrue(entitlements.getPermissions().getVehicleModule());
        assertTrue(entitlements.getPermissions().getNoAds());
        assertEquals(10, entitlements.getLimits().getAccounts());
        assertEquals(5, entitlements.getLimits().getCreditCards());
        assertEquals(2, entitlements.getLimits().getVehicles());
    }

    private UserEntitlementsDTO buildFor(EffectivePlan plan) {
        ClosedTestTesterRepository repository = mock(ClosedTestTesterRepository.class);
        EntitlementServiceImp service = new EntitlementServiceImp(
                repository,
                mock(EntitlementReconciliationService.class)
        );
        Users user = Users.builder()
                .email("common@controleja.local")
                .plan(plan)
                .build();
        return service.buildForUser(user);
    }
}
