package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleRefuelMetricsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleRefuelMetricsService service;

    @Test
    void recalculate_shouldUseFullTankCycleAndIgnorePartialEfficiency() {
        Vehicle vehicle = Vehicle.builder().id(UUID.randomUUID()).build();
        Transactions baseline = refuel(1000L, 1000L, "10000.0", 40.0, FuelType.ETANOL, true);
        Transactions partial = refuel(2000L, 2000L, "10100.0", 10.0, FuelType.ETANOL, false);
        Transactions closingFull = refuel(3000L, 3000L, "10400.0", 30.0, FuelType.ETANOL, true);
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(vehicle.getId(), Long.MAX_VALUE))
                .thenReturn(List.of(closingFull, partial, baseline));

        service.recalculate(vehicle);

        ArgumentCaptor<List<Transactions>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<Transactions> saved = captor.getValue();
        assertNull(saved.get(0).getEfficiency());
        assertNull(saved.get(1).getEfficiency());
        assertEquals(10.0, saved.get(2).getEfficiency());
        assertEquals(10.0, vehicle.getAvgKmPerLiterEthanol());
        assertNull(vehicle.getAvgKmPerLiterGasoline());
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void recalculate_whenNoRefuelsRemain_shouldClearVehicleAverages() {
        Vehicle vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .avgKmPerLiterGasoline(10.0)
                .avgKmPerLiterEthanol(8.0)
                .build();
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(vehicle.getId(), Long.MAX_VALUE))
                .thenReturn(List.of());

        service.recalculate(vehicle);

        assertNull(vehicle.getAvgKmPerLiterGasoline());
        assertNull(vehicle.getAvgKmPerLiterEthanol());
        verify(vehicleRepository).save(vehicle);
    }

    private Transactions refuel(
            long date,
            long createdAt,
            String odometer,
            Double liters,
            FuelType fuelType,
            boolean fullTank) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .date(date)
                .createdAt(createdAt)
                .currentOdometer(new BigDecimal(odometer))
                .liters(liters)
                .fuelType(fuelType)
                .fullTank(fullTank)
                .build();
    }
}
