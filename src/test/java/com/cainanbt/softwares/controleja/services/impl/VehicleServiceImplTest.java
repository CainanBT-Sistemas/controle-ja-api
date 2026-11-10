package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.services.entitlements.EntitlementMutationGuard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class VehicleServiceImplTest {

    @Test
    void shouldAllowInternalOdometerRecalculationForRestrictedVehicle() {
        VehicleRepository repository = mock(VehicleRepository.class);
        EntitlementMutationGuard entitlementGuard = mock(EntitlementMutationGuard.class);
        VehicleServiceImpl service = new VehicleServiceImpl(repository, entitlementGuard);
        Vehicle vehicle = Vehicle.builder()
                .planRestricted(true)
                .currentOdometer(new BigDecimal("1000.0"))
                .build();

        service.setCurrentOdometer(vehicle, new BigDecimal("900.0"));

        assertEquals(new BigDecimal("900.0"), vehicle.getCurrentOdometer());
        verify(repository).save(vehicle);
        verifyNoInteractions(entitlementGuard);
    }
}
