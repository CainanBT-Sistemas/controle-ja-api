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
    void recalculate_shouldUseFirstRefuelAsBaselineAndRebuildFollowingEfficiencies() {
        Vehicle vehicle = Vehicle.builder().id(UUID.randomUUID()).build();
        Transactions first = refuel(1000L, 1000L, "10000.0", 20.0, FuelType.ETANOL);
        Transactions second = refuel(2000L, 2000L, "10200.0", 20.0, FuelType.ETANOL);
        Transactions third = refuel(3000L, 3000L, "10500.0", 30.0, FuelType.GASOLINA);
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(vehicle.getId(), Long.MAX_VALUE))
                .thenReturn(List.of(third, second, first));

        service.recalculate(vehicle);

        ArgumentCaptor<List<Transactions>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<Transactions> saved = captor.getValue();
        assertNull(saved.get(0).getEfficiency());
        assertEquals(10.0, saved.get(1).getEfficiency());
        assertEquals(10.0, saved.get(2).getEfficiency());
        assertEquals(10.0, vehicle.getAvgKmPerLiterEthanol());
        assertEquals(10.0, vehicle.getAvgKmPerLiterGasoline());
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
            FuelType fuelType) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .date(date)
                .createdAt(createdAt)
                .currentOdometer(new BigDecimal(odometer))
                .liters(liters)
                .fuelType(fuelType)
                .build();
    }
}
