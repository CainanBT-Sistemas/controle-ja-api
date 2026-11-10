package com.cainanbt.softwares.controleja.services.entitlements;

import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementMutationGuardTest {

    @Test
    void shouldReconcileCurrentPlanBeforeCountingAvailableResources() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        EntitlementReconciliationService reconciliationService = mock(EntitlementReconciliationService.class);
        UUID userId = UUID.randomUUID();
        Users principal = Users.builder().id(userId).build();
        Users persisted = Users.builder().id(userId).entitlementTouchedAt(0L).build();
        when(usersRepository.findById(userId)).thenReturn(Optional.of(persisted));
        when(usersRepository.saveAndFlush(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(entitlementService.resolvePlan(persisted)).thenReturn(EffectivePlan.FREE);
        EntitlementMutationGuard guard = new EntitlementMutationGuard(
                usersRepository,
                entitlementService,
                reconciliationService
        );

        PlanLimits limits = guard.touchAndGetLimits(principal);

        verify(reconciliationService).reconcile(persisted, EffectivePlan.FREE);
        assertEquals(EffectivePlan.FREE.getLimits(), limits);
    }

    @Test
    void shouldReconcileCurrentPlanBeforeMutation() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EntitlementService entitlementService = mock(EntitlementService.class);
        EntitlementReconciliationService reconciliationService = mock(EntitlementReconciliationService.class);
        UUID userId = UUID.randomUUID();
        Users principal = Users.builder().id(userId).build();
        Users persisted = Users.builder().id(userId).build();
        when(usersRepository.findById(userId)).thenReturn(Optional.of(persisted));
        when(entitlementService.resolvePlan(persisted)).thenReturn(EffectivePlan.FREE);
        EntitlementMutationGuard guard = new EntitlementMutationGuard(
                usersRepository,
                entitlementService,
                reconciliationService
        );

        guard.reconcileForMutation(principal);

        verify(reconciliationService).reconcile(persisted, EffectivePlan.FREE);
    }

    @Test
    void shouldRejectRestrictedCardAndVehicle() {
        EntitlementMutationGuard guard = new EntitlementMutationGuard(
                mock(UsersRepository.class),
                mock(EntitlementService.class),
                mock(EntitlementReconciliationService.class)
        );

        assertThrows(
                BadRequestException.class,
                () -> guard.requireAvailable(CreditCard.builder().planRestricted(true).build())
        );
        assertThrows(
                BadRequestException.class,
                () -> guard.requireAvailable(Vehicle.builder().planRestricted(true).build())
        );
    }
}
