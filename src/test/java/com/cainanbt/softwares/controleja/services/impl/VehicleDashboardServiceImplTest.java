package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VehicleDashboardServiceImplTest {

    @Mock
    private VehicleService vehicleService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private VehicleLogRepository logRepository;

    @InjectMocks
    private VehicleDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionRepository.findPreviousValidRefuelsByVehicleBeforeDate(any(UUID.class), anyLong()))
                .thenReturn(List.of());
        lenient().when(transactionRepository.getNetVehicleCost(any(UUID.class), anyLong(), anyLong()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(transactionRepository.getNetVehicleInstallmentCost(any(UUID.class), anyLong(), anyLong()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void getDashboard_whenMayHasRefuel_shouldReturnMayAverageKml() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "10100.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions refuel = refuel(epoch(2026, 5, 10), "10000.00", 20.0, 10.0);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("300.00"), new BigDecimal("1200.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(refuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end))
                .thenReturn(List.of(log(epoch(2026, 5, 1), "9900.00"), log(epoch(2026, 5, 31), "10100.00")));

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(10.0, dto.getCurrentAvgKml());
        assertEquals(new BigDecimal("300.00"), dto.getMonthlyCost());
        assertEquals(new BigDecimal("1.50"), dto.getCostPerKm());
    }

    @Test
    void getDashboard_whenAprilHasNoRefuelsOrLogs_shouldNotUseMayOrGlobalAverage() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .currentOdometer(new BigDecimal("10000.00"))
                .avgKmPerLiterGasoline(10.0)
                .build();
        long start = epoch(2026, 4, 1);
        long end = epoch(2026, 4, 30);

        stubCommon(vehicleId, vehicle, start, end, BigDecimal.ZERO, new BigDecimal("1200.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertNull(dto.getCurrentAvgKml());
        assertEquals(BigDecimal.ZERO, dto.getMonthlyCost());
        assertEquals(BigDecimal.ZERO, dto.getCostPerKm());
    }

    @Test
    void getDashboard_whenTwoVehiclesHaveRefuelsInSameMonth_shouldUseOnlyRequestedVehicle() {
        UUID vehicleAId = UUID.randomUUID();
        UUID vehicleBId = UUID.randomUUID();
        Vehicle vehicleA = vehicle(vehicleAId, "5050.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions vehicleARefuel = refuel(epoch(2026, 5, 8), "5000.00", 10.0, 8.0);

        stubCommon(vehicleAId, vehicleA, start, end, new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleAId, start, end)).thenReturn(List.of(vehicleARefuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleAId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleAId, start, end);

        assertEquals(8.0, dto.getCurrentAvgKml());
        assertEquals(new BigDecimal("100.00"), dto.getMonthlyCost());
        verify(transactionRepository, never()).findRefuelsByVehicleAndDateBetween(eq(vehicleBId), anyLong(), anyLong());
        verify(transactionRepository, never()).getNetVehicleCost(eq(vehicleBId), anyLong(), anyLong());
    }

    @Test
    void getDashboard_whenMaintenanceTransactionExists_shouldEnterMonthlyCostAndNotAffectAverageKml() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "10000.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("450.00"), new BigDecimal("450.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("450.00"), dto.getMonthlyCost());
        assertNull(dto.getCurrentAvgKml());
    }

    @Test
    void getDashboard_whenVehicleRevenueExists_shouldDeductFromMonthlyCost() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1100.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("80.00"), new BigDecimal("80.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end))
                .thenReturn(List.of(log(epoch(2026, 5, 1), "1000.00"), log(epoch(2026, 5, 31), "1100.00")));

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("80.00"), dto.getMonthlyCost());
        assertEquals(new BigDecimal("0.80"), dto.getCostPerKm());
    }

    @Test
    void getDashboard_whenForecastHasNoValidFutureDate_shouldNotReturnEstimatedCost() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        Vehicle vehicle = vehicle(vehicleId, "10100.00");
        Transactions lastRefuel = refuel(now - (2L * 24L * 60L * 60L * 1000L), "10000.00", 40.0, 10.0);
        lastRefuel.setAmount(new BigDecimal("200.00"));
        long start = currentMonthStart();
        long end = currentMonthEnd();

        stubCommon(vehicleId, vehicle, start, end, BigDecimal.ZERO, BigDecimal.ZERO);
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(lastRefuel));
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong())).thenReturn(List.of(lastRefuel));
        when(logRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(eq(vehicleId), anyLong())).thenReturn(Optional.empty());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertNull(dto.getEstimatedNextRefuelDate());
        assertEquals(BigDecimal.ZERO, dto.getEstimatedNextRefuelCost());
        assertEquals(BigDecimal.ZERO, dto.getEstimatedNextCost());
        assertEquals(BigDecimal.ZERO, dto.getNextMonthEstimatedCost());
        assertNull(dto.getNextMonthEstimatedCostConfidence());
        assertNull(dto.getNextRefuelPrediction());
        assertTrue(dto.getFuturePredictions().isEmpty());
        assertTrue(dto.getRemainingKms() > 0);
    }

    @Test
    void getDashboard_whenMonthsInSameYearDiffer_shouldKeepYearlyCostAndVaryPeriodFields() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "10200.00");
        long aprilStart = epoch(2026, 4, 1);
        long aprilEnd = epoch(2026, 4, 30);
        long mayStart = epoch(2026, 5, 1);
        long mayEnd = epoch(2026, 5, 31);
        long startOfYear = epoch(2026, 1, 1);
        long endOfYear = epoch(2026, 12, 31) + (24L * 60L * 60L * 1000L) - 1;

        when(vehicleService.findByIdOrThrow(vehicleId)).thenReturn(vehicle);
        lenient().when(transactionRepository.getNetVehicleCost(vehicleId, aprilStart, aprilEnd)).thenReturn(new BigDecimal("50.00"));
        lenient().when(transactionRepository.getNetVehicleCost(vehicleId, mayStart, mayEnd)).thenReturn(new BigDecimal("300.00"));
        lenient().when(transactionRepository.getNetVehicleCost(vehicleId, startOfYear, endOfYear)).thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, aprilStart, aprilEnd)).thenReturn(List.of());
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, mayStart, mayEnd))
                .thenReturn(List.of(refuel(epoch(2026, 5, 10), "10000.00", 20.0, 10.0)));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, aprilStart, aprilEnd)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, mayStart, mayEnd))
                .thenReturn(List.of(log(mayStart, "10000.00"), log(mayEnd, "10200.00")));

        VehicleDashboardDTO april = service.getDashboard(vehicleId, aprilStart, aprilEnd);
        VehicleDashboardDTO may = service.getDashboard(vehicleId, mayStart, mayEnd);

        assertEquals(new BigDecimal("1000.00"), april.getYearlyCost());
        assertEquals(new BigDecimal("1000.00"), may.getYearlyCost());
        assertEquals(new BigDecimal("50.00"), april.getMonthlyCost());
        assertEquals(new BigDecimal("300.00"), may.getMonthlyCost());
        assertNull(april.getCurrentAvgKml());
        assertEquals(10.0, may.getCurrentAvgKml());
        assertEquals(BigDecimal.ZERO, april.getCostPerKm());
        assertEquals(new BigDecimal("1.50"), may.getCostPerKm());
    }

    @Test
    void getDashboard_whenForecastHasValidData_shouldReturnFutureDateOnly() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .currentOdometer(new BigDecimal("10100.00"))
                .tankCapacity(40.0)
                .build();
        Transactions lastRefuel = refuel(now - (2L * 24L * 60L * 60L * 1000L), "10000.00", 40.0, 10.0);
        lastRefuel.setAmount(new BigDecimal("200.00"));
        VehicleLog firstLog = log(now - (10L * 24L * 60L * 60L * 1000L), "9900.00");
        long start = currentMonthStart();
        long end = currentMonthEnd();

        stubCommon(vehicleId, vehicle, start, end, BigDecimal.ZERO, BigDecimal.ZERO);
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(lastRefuel));
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong())).thenReturn(List.of(lastRefuel));
        when(logRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(eq(vehicleId), anyLong())).thenReturn(Optional.of(firstLog));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertTrue(dto.getEstimatedNextRefuelDate() >= now);
        assertTrue(dto.getRemainingKms() > 0);
        assertEquals(new BigDecimal("200.00"), dto.getEstimatedNextRefuelCost());
        assertEquals(new BigDecimal("200.00"), dto.getNextRefuelPrediction().getEstimatedCost());
        assertEquals("LOW", dto.getNextRefuelPrediction().getConfidence());
    }

    @Test
    void getDashboard_whenRefuelsExistAcrossSeveralMonths_shouldForecastDateAndUseMonthlyFuelCostAverage() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        long day = 24L * 60L * 60L * 1000L;
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .currentOdometer(new BigDecimal("1950.00"))
                .tankCapacity(40.0)
                .build();

        Transactions februaryRefuel = refuel(now - (95L * day), "1000.00", 30.0, 10.0, "150.00");
        Transactions marchRefuel = refuel(now - (65L * day), "1300.00", 30.0, 10.0, "180.00");
        Transactions aprilRefuel = refuel(now - (35L * day), "1600.00", 30.0, 10.0, "210.00");
        Transactions mayRefuel = refuel(now - (5L * day), "1900.00", 30.0, 10.0, "240.00");
        long start = currentMonthStart();
        long end = currentMonthEnd();

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("240.00"), new BigDecimal("780.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(mayRefuel));
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong()))
                .thenReturn(List.of(mayRefuel, aprilRefuel, marchRefuel, februaryRefuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertTrue(dto.getEstimatedNextRefuelDate() >= now);
        assertTrue(dto.getRemainingKms() > 0);
        assertEquals(new BigDecimal("260.00"), dto.getEstimatedNextRefuelCost());
        assertEquals(new BigDecimal("260.00"), dto.getNextRefuelPrediction().getEstimatedCost());
        assertEquals(40.0, dto.getNextRefuelPrediction().getEstimatedLiters());
        assertEquals(FuelType.GASOLINA, dto.getNextRefuelPrediction().getFuelType());
    }

    @Test
    void getDashboard_whenVehicleHasFuelAndOtherMonthlyCosts_shouldForecastTotalNextCostFromUserProfile() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        long day = 24L * 60L * 60L * 1000L;
        Vehicle vehicle = Vehicle.builder()
                .id(vehicleId)
                .currentOdometer(new BigDecimal("1950.00"))
                .tankCapacity(40.0)
                .build();

        Transactions februaryRefuel = refuel(now - (95L * day), "1000.00", 30.0, 10.0, "150.00");
        Transactions marchRefuel = refuel(now - (65L * day), "1300.00", 30.0, 10.0, "180.00");
        Transactions aprilRefuel = refuel(now - (35L * day), "1600.00", 30.0, 10.0, "210.00");
        Transactions mayRefuel = refuel(now - (5L * day), "1900.00", 30.0, 10.0, "240.00");
        long start = currentMonthStart();
        long end = currentMonthEnd();

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("1200.00"), new BigDecimal("3300.00"));
        stubVehicleCostMonth(vehicleId, DateUtils.epochToLocalDate(now), new BigDecimal("1200.00"));
        stubVehicleCostMonth(vehicleId, DateUtils.epochToLocalDate(now).minusMonths(1), new BigDecimal("600.00"));
        stubVehicleCostMonth(vehicleId, DateUtils.epochToLocalDate(now).minusMonths(2), new BigDecimal("300.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(mayRefuel));
        when(transactionRepository.findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong()))
                .thenReturn(List.of(mayRefuel, aprilRefuel, marchRefuel, februaryRefuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("260.00"), dto.getEstimatedNextRefuelCost());
        assertEquals(new BigDecimal("700.00"), dto.getEstimatedNextCost());
        assertEquals(new BigDecimal("700.00"), dto.getNextMonthEstimatedCost());
        assertEquals("MEDIUM", dto.getNextMonthEstimatedCostConfidence());
        assertEquals(3, dto.getFuturePredictions().size());
    }

    @Test
    void getDashboard_whenFutureInstallmentsExist_shouldPreferScheduledNextMonthCost() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        Vehicle vehicle = vehicle(vehicleId, "10100.00");
        long start = currentMonthStart();
        long end = currentMonthEnd();
        YearMonth nextMonth = YearMonth.from(DateUtils.epochToLocalDate(now)).plusMonths(1);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("100.00"), new BigDecimal("100.00"));
        stubVehicleCostMonth(vehicleId, DateUtils.epochToLocalDate(now), new BigDecimal("100.00"));
        stubVehicleInstallmentCostMonth(vehicleId, nextMonth, new BigDecimal("950.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("950.00"), dto.getEstimatedNextCost());
        assertEquals(new BigDecimal("950.00"), dto.getNextMonthEstimatedCost());
        assertEquals("MEDIUM", dto.getNextMonthEstimatedCostConfidence());
        assertEquals(nextMonth.toString(), dto.getFuturePredictions().get(0).getMonth());
    }

    @Test
    void getDashboard_whenSelectedPeriodIsPastMonth_shouldHideFuturePredictionFields() {
        UUID vehicleId = UUID.randomUUID();
        long now = DateUtils.getEpochNow();
        long day = 24L * 60L * 60L * 1000L;
        Vehicle vehicle = vehicle(vehicleId, "10100.00");
        Transactions lastRefuel = refuel(now - (2L * day), "10000.00", 40.0, 10.0, "200.00");
        LocalDate pastMonth = DateUtils.epochToLocalDate(now).minusMonths(1).withDayOfMonth(1);
        long start = DateUtils.localDateToEpoch(pastMonth);
        long end = DateUtils.localDateToEpoch(pastMonth.plusMonths(1)) - 1;

        stubCommon(vehicleId, vehicle, start, end, BigDecimal.ZERO, BigDecimal.ZERO);
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(lastRefuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertNull(dto.getRemainingKms());
        assertNull(dto.getEstimatedNextRefuelDate());
        assertEquals(BigDecimal.ZERO, dto.getEstimatedNextRefuelCost());
        assertEquals(BigDecimal.ZERO, dto.getEstimatedNextCost());
        assertEquals(BigDecimal.ZERO, dto.getNextMonthEstimatedCost());
        assertNull(dto.getNextMonthEstimatedCostConfidence());
        assertNull(dto.getNextRefuelPrediction());
        assertTrue(dto.getFuturePredictions().isEmpty());
        verify(transactionRepository, never()).findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong());
    }

    @Test
    void getDashboard_whenMonthHasTwoRefuels_shouldCalculateLastRefuelDistanceAndKml() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1220.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions previous = refuel(epoch(2026, 5, 5), "1000.00", 20.0, 10.0, "200.00");
        Transactions last = refuel(epoch(2026, 5, 20), "1220.00", 20.0, 11.0, "220.00");

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("420.00"), new BigDecimal("420.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(previous, last));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("220.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("11.00"), dto.getLastFuelPricePerLiter());
        assertEquals(220.0, dto.getLastRefuelDistanceKm());
        assertEquals(11.0, dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenTwoRefuelsHaveSameDate_shouldUseCreatedAtToFindPreviousInPeriod() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1220.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        long refuelDate = epoch(2026, 5, 20);
        Transactions previous = refuel(refuelDate, "1000.00", 20.0, 10.0, "200.00", 1000L);
        Transactions last = refuel(refuelDate, "1220.00", 20.0, 11.0, "220.00", 2000L);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("420.00"), new BigDecimal("420.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(previous, last));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("220.00"), dto.getLastRefuelAmount());
        assertEquals(220.0, dto.getLastRefuelDistanceKm());
        assertEquals(11.0, dto.getLastRefuelKml());
    }

    @Test
    void getDashboard_whenMonthHasOneRefuelAndPreviousMonthRefuel_shouldUsePreviousForDistance() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions aprilRefuel = refuel(epoch(2026, 4, 25), "1000.00", 30.0, 9.0, "180.00");
        Transactions mayRefuel = refuel(epoch(2026, 5, 15), "1250.00", 25.0, 10.0, "150.00");

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("150.00"), new BigDecimal("330.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(mayRefuel));
        when(transactionRepository.findPreviousValidRefuelsByVehicleBeforeDate(vehicleId, mayRefuel.getDate())).thenReturn(List.of(aprilRefuel));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("150.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("6.00"), dto.getLastFuelPricePerLiter());
        assertEquals(250.0, dto.getLastRefuelDistanceKm());
        assertEquals(10.0, dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenFirstRefuelHasNoPreviousHistory_shouldReturnAmountPriceAndFuelTypeOnly() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions firstRefuel = refuel(epoch(2026, 5, 15), "1250.00", 25.0, 10.0, "150.00");

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("150.00"), new BigDecimal("150.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(firstRefuel));
        when(transactionRepository.findPreviousValidRefuelsByVehicleBeforeDate(vehicleId, firstRefuel.getDate())).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("150.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("6.00"), dto.getLastFuelPricePerLiter());
        assertNull(dto.getLastRefuelDistanceKm());
        assertNull(dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenMonthHasNoRefuel_shouldReturnNullLastRefuelFields() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 6, 1);
        long end = epoch(2026, 6, 30);

        stubCommon(vehicleId, vehicle, start, end, BigDecimal.ZERO, new BigDecimal("330.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of());
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertNull(dto.getLastRefuelAmount());
        assertNull(dto.getLastFuelPricePerLiter());
        assertNull(dto.getLastRefuelDistanceKm());
        assertNull(dto.getLastRefuelKml());
        assertNull(dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenTwoVehiclesHaveRefuels_shouldNotMixLastRefuelData() {
        UUID vehicleAId = UUID.randomUUID();
        UUID vehicleBId = UUID.randomUUID();
        Vehicle vehicleA = vehicle(vehicleAId, "2100.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions vehicleAPrevious = refuel(epoch(2026, 5, 3), "1900.00", 20.0, 10.0, "100.00");
        Transactions vehicleALast = refuel(epoch(2026, 5, 18), "2100.00", 20.0, 10.0, "120.00");

        stubCommon(vehicleAId, vehicleA, start, end, new BigDecimal("220.00"), new BigDecimal("220.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleAId, start, end)).thenReturn(List.of(vehicleAPrevious, vehicleALast));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleAId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleAId, start, end);

        assertEquals(new BigDecimal("120.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("6.00"), dto.getLastFuelPricePerLiter());
        assertEquals(200.0, dto.getLastRefuelDistanceKm());
        assertEquals(10.0, dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
        verify(transactionRepository, never()).findRefuelsByVehicleAndDateBetween(eq(vehicleBId), anyLong(), anyLong());
        verify(transactionRepository, never()).findPreviousValidRefuelsByVehicleBeforeDate(eq(vehicleBId), anyLong());
    }

    @Test
    void getDashboard_whenPreviousOdometerIsGreaterThanLast_shouldNotReturnNegativeDistanceOrKml() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions previous = refuel(epoch(2026, 4, 20), "1300.00", 20.0, 10.0, "120.00");
        Transactions last = refuel(epoch(2026, 5, 10), "1250.00", 25.0, 10.0, "150.00");

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("150.00"), new BigDecimal("270.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(last));
        when(transactionRepository.findPreviousValidRefuelsByVehicleBeforeDate(vehicleId, last.getDate())).thenReturn(List.of(previous));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("150.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("6.00"), dto.getLastFuelPricePerLiter());
        assertNull(dto.getLastRefuelDistanceKm());
        assertNull(dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenLatestRefuelHasAbsurdLitersAndOdometer_shouldIgnoreOutlierForFuelMetrics() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "280980.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        long refuelDate = epoch(2026, 5, 20);
        Transactions invalidLargeLiters = refuel(refuelDate, "1500.00", 500.0, null, "180.00", 1000L);
        Transactions refuel2000 = refuel(refuelDate, "2000.00", 50.0, 10.0, "200.00", 2000L);
        Transactions refuel2280 = refuel(refuelDate, "2280.00", 41.0, 6.829268292682927, "190.00", 3000L);
        Transactions refuel2480 = refuel(refuelDate, "2480.00", 30.0, 6.666666666666667, "200.00", 4000L);
        Transactions expectedLast = refuel(refuelDate, "2780.00", 45.0, 6.666666666666667, "350.00", 5000L);
        Transactions invalidOdometerJump = refuel(refuelDate, "280980.00", 450.0, 6177.777777777777, "350.00", 6000L);

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("2070.00"), new BigDecimal("2070.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end))
                .thenReturn(List.of(invalidLargeLiters, refuel2000, refuel2280, refuel2480, expectedLast, invalidOdometerJump));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertEquals(new BigDecimal("350.00"), dto.getLastRefuelAmount());
        assertEquals(new BigDecimal("7.78"), dto.getLastFuelPricePerLiter());
        assertEquals(300.0, dto.getLastRefuelDistanceKm());
        assertEquals(6.67, dto.getLastRefuelKml());
        assertEquals(FuelType.GASOLINA, dto.getLastRefuelFuelType());
        assertTrue(dto.getCurrentAvgKml() < 100.0);
    }

    @Test
    void getDashboard_whenRefuelHasZeroOrNullLiters_shouldReturnNullLastRefuelFieldsWithoutBreaking() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 5, 1);
        long end = epoch(2026, 5, 31);
        Transactions zeroLiters = refuel(epoch(2026, 5, 10), "1250.00", 0.0, 10.0, "100.00");
        Transactions nullLiters = refuel(epoch(2026, 5, 20), "1260.00", null, 10.0, "100.00");

        stubCommon(vehicleId, vehicle, start, end, new BigDecimal("200.00"), new BigDecimal("200.00"));
        when(transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, start, end)).thenReturn(List.of(zeroLiters, nullLiters));
        when(logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end)).thenReturn(List.of());

        VehicleDashboardDTO dto = service.getDashboard(vehicleId, start, end);

        assertNull(dto.getLastRefuelAmount());
        assertNull(dto.getLastFuelPricePerLiter());
        assertNull(dto.getLastRefuelDistanceKm());
        assertNull(dto.getLastRefuelKml());
        assertNull(dto.getLastRefuelFuelType());
    }

    @Test
    void getDashboard_whenPeriodIsInvalid_shouldRejectBeforeQueryingHeavyData() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = vehicle(vehicleId, "1250.00");
        long start = epoch(2026, 6, 30);
        long end = epoch(2026, 6, 1);

        when(vehicleService.findByIdOrThrow(vehicleId)).thenReturn(vehicle);

        assertThrows(BadRequestException.class, () -> service.getDashboard(vehicleId, start, end));
        verify(transactionRepository, never()).getNetVehicleCost(eq(vehicleId), anyLong(), anyLong());
    }

    private void stubCommon(UUID vehicleId, Vehicle vehicle, long start, long end, BigDecimal monthlyCost, BigDecimal yearlyCost) {
        LocalDate selectedPeriod = DateUtils.epochToLocalDate(start);
        long startOfYear = DateUtils.localDateToEpoch(selectedPeriod.withDayOfYear(1));
        long endOfYear = DateUtils.localDateToEpoch(selectedPeriod.with(TemporalAdjusters.lastDayOfYear()).plusDays(1)) - 1;

        when(vehicleService.findByIdOrThrow(vehicleId)).thenReturn(vehicle);
        when(transactionRepository.getNetVehicleCost(vehicleId, start, end)).thenReturn(monthlyCost);
        when(transactionRepository.getNetVehicleCost(vehicleId, startOfYear, endOfYear)).thenReturn(yearlyCost);
        lenient().when(transactionRepository.findValidRefuelsByVehicleUpToDate(eq(vehicleId), anyLong())).thenReturn(List.of());
    }

    private void stubVehicleCostMonth(UUID vehicleId, LocalDate monthDate, BigDecimal cost) {
        LocalDate startDate = monthDate.withDayOfMonth(1);
        long start = DateUtils.localDateToEpoch(startDate);
        long end = DateUtils.localDateToEpoch(startDate.plusMonths(1)) - 1;
        when(transactionRepository.getNetVehicleCost(vehicleId, start, end)).thenReturn(cost);
    }

    private void stubVehicleInstallmentCostMonth(UUID vehicleId, YearMonth month, BigDecimal cost) {
        LocalDate startDate = month.atDay(1);
        long start = DateUtils.localDateToEpoch(startDate);
        long end = DateUtils.localDateToEpoch(startDate.plusMonths(1)) - 1;
        when(transactionRepository.getNetVehicleInstallmentCost(vehicleId, start, end)).thenReturn(cost);
    }

    private Vehicle vehicle(UUID vehicleId, String currentOdometer) {
        return Vehicle.builder()
                .id(vehicleId)
                .currentOdometer(new BigDecimal(currentOdometer))
                .tankCapacity(40.0)
                .build();
    }

    private Transactions refuel(long date, String currentOdometer, Double liters, Double efficiency) {
        return refuel(date, currentOdometer, liters, efficiency, "100.00");
    }

    private Transactions refuel(long date, String currentOdometer, Double liters, Double efficiency, String amount) {
        return refuel(date, currentOdometer, liters, efficiency, amount, date);
    }

    private Transactions refuel(long date, String currentOdometer, Double liters, Double efficiency, String amount, Long createdAt) {
        return Transactions.builder()
                .id(UUID.randomUUID())
                .date(date)
                .currentOdometer(new BigDecimal(currentOdometer))
                .liters(liters)
                .efficiency(efficiency)
                .fuelType(FuelType.GASOLINA)
                .amount(new BigDecimal(amount))
                .createdAt(createdAt)
                .build();
    }

    private VehicleLog log(long date, String odometerReading) {
        return VehicleLog.builder()
                .id(UUID.randomUUID())
                .date(date)
                .odometerReading(new BigDecimal(odometerReading))
                .build();
    }

    private long epoch(int year, int month, int day) {
        return DateUtils.localDateToEpoch(LocalDate.of(year, month, day));
    }

    private long currentMonthStart() {
        LocalDate currentMonth = DateUtils.epochToLocalDate(DateUtils.getEpochNow()).withDayOfMonth(1);
        return DateUtils.localDateToEpoch(currentMonth);
    }

    private long currentMonthEnd() {
        LocalDate nextMonth = DateUtils.epochToLocalDate(DateUtils.getEpochNow()).withDayOfMonth(1).plusMonths(1);
        return DateUtils.localDateToEpoch(nextMonth) - 1;
    }
}
