package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.repositories.GasStationRankingRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GasStationRankingServiceImplTest {

    @Mock
    private GasStationRankingRepository repository;

    @Mock
    private VehicleLogRepository vehicleLogRepository;

    @InjectMocks
    private GasStationRankingServiceImpl service;

    @Test
    void updateRanking_shouldNormalizeCityAndRoadToMixedCycleScore() {
        GasStation station = gasStation();
        Transactions cityTx = refuel(station, 9.0, DrivingPredominance.CITY);
        when(repository.findByGasStationAndFuelType(station, FuelType.GASOLINA)).thenReturn(Optional.empty());

        service.updateRanking(cityTx);

        ArgumentCaptor<GasStationRanking> cityCaptor = ArgumentCaptor.forClass(GasStationRanking.class);
        verify(repository).save(cityCaptor.capture());
        GasStationRanking cityRanking = cityCaptor.getValue();

        GasStation roadStation = gasStation();
        Transactions roadTx = refuel(roadStation, 11.2, DrivingPredominance.ROAD);
        when(repository.findByGasStationAndFuelType(roadStation, FuelType.GASOLINA)).thenReturn(Optional.empty());

        service.updateRanking(roadTx);

        ArgumentCaptor<GasStationRanking> allCaptor = ArgumentCaptor.forClass(GasStationRanking.class);
        verify(repository, org.mockito.Mockito.times(2)).save(allCaptor.capture());
        GasStationRanking roadRanking = allCaptor.getAllValues().get(1);

        assertEquals(10.0, cityRanking.getAdjustedAvgKml(), 0.01);
        assertEquals(10.0, roadRanking.getAdjustedAvgKml(), 0.01);
        assertEquals(cityRanking.getScore(), roadRanking.getScore(), 0.01);
        assertEquals(1, cityRanking.getCityRefuelCount());
        assertEquals(1, roadRanking.getRoadRefuelCount());
    }

    @Test
    void updateRanking_whenTransactionHasNoPredominance_shouldUseLatestVehicleLog() {
        GasStation station = gasStation();
        Vehicle vehicle = Vehicle.builder().id(UUID.randomUUID()).build();
        Transactions tx = refuel(station, 9.0, null);
        tx.setVehicle(vehicle);
        VehicleLog log = VehicleLog.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .date(tx.getDate())
                .drivingPredominance(DrivingPredominance.CITY)
                .build();

        when(repository.findByGasStationAndFuelType(station, FuelType.GASOLINA)).thenReturn(Optional.empty());
        when(vehicleLogRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateDesc(vehicle.getId(), tx.getDate()))
                .thenReturn(Optional.of(log));

        service.updateRanking(tx);

        ArgumentCaptor<GasStationRanking> captor = ArgumentCaptor.forClass(GasStationRanking.class);
        verify(repository).save(captor.capture());

        assertEquals(10.0, captor.getValue().getAdjustedAvgKml(), 0.01);
        assertEquals(1, captor.getValue().getCityRefuelCount());
    }

    @Test
    void updateRanking_whenRefuelHasNoUsableEfficiency_shouldIgnore() {
        Transactions tx = refuel(gasStation(), null, DrivingPredominance.CITY);

        service.updateRanking(tx);

        verify(repository, never()).save(any());
    }

    private Transactions refuel(GasStation station, Double efficiency, DrivingPredominance predominance) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .date(DateUtils.getEpochNow())
                .amount(new BigDecimal("100.00"))
                .liters(10.0)
                .efficiency(efficiency)
                .fuelType(FuelType.GASOLINA)
                .gasStation(station)
                .drivingPredominance(predominance)
                .build();
    }

    private GasStation gasStation() {
        Users user = Users.builder().id(UUID.randomUUID()).build();
        return GasStation.builder()
                .id(UUID.randomUUID())
                .name("Posto Teste")
                .user(user)
                .createdAt(DateUtils.getEpochNow())
                .build();
    }
}
