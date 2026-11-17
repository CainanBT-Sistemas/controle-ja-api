package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementPermissionsDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementLimitsDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.repositories.ClosedTestTesterRepository;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import com.cainanbt.softwares.controleja.services.billing.BillingRights;
import com.cainanbt.softwares.controleja.services.entitlements.EntitlementReconciliationService;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EntitlementServiceImp implements EntitlementService {

    private final ClosedTestTesterRepository testerRepository;
    private final EntitlementReconciliationService reconciliationService;
    private final BillingRights billingRights;

    public EntitlementServiceImp(
            ClosedTestTesterRepository testerRepository,
            EntitlementReconciliationService reconciliationService,
            BillingRights billingRights
    ) {
        this.testerRepository = testerRepository;
        this.reconciliationService = reconciliationService;
        this.billingRights = billingRights;
    }

    @Override
    public UserEntitlementsDTO buildForUser(Users user) {
        EffectivePlan plan = resolvePlan(user);
        reconciliationService.reconcile(user, plan);
        return new UserEntitlementsDTO(
                plan.name(),
                plan.isTester(),
                plan.isPlusEquivalent(),
                plan.isAdsEnabled(),
                UserEntitlementLimitsDTO.from(plan.getLimits()),
                new UserEntitlementPermissionsDTO(plan.hasVehicleModule(), plan.hasNoAds())
        );
    }

    @Override
    public EffectivePlan resolvePlan(Users user) {
        EffectivePlan plan = EffectivePlan.FREE;
        if (user != null) {
            plan = user.getPlan() == null
                    ? EffectivePlan.FREE
                    : user.getPlan().asPersistedBasePlan();
            EffectivePlan purchased = billingRights.resolve(user.getId());
            if (purchased == EffectivePlan.PLUS || (plan == EffectivePlan.FREE && purchased == EffectivePlan.FREE_NO_ADS)) {
                plan = purchased;
            }
            if (user.getEmail() != null) {
                String normalizedEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
                plan = testerRepository.findByNormalizedEmailAndEnabledTrue(normalizedEmail)
                        .map(tester -> tester.getProfile() == null
                                ? EffectivePlan.TESTER_PLUS
                                : tester.getProfile())
                        .orElse(plan);
            }
        }
        return plan;
    }
}
