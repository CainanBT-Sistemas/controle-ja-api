package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleDashboardService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleDashboardServiceImpl implements VehicleDashboardService {

    private final VehicleService vehicleService;
    private final TransactionRepository transactionRepository;
    private final VehicleLogRepository logRepository;

    @Override
    public VehicleDashboardDTO getDashboard(UUID vehicleId, Long startOfMonth, Long endOfMonth) {
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);

        // 1. Custos: Mês atual
        BigDecimal monthlyCost = transactionRepository.getNetVehicleCost(vehicleId, startOfMonth, endOfMonth);
        if (monthlyCost == null) monthlyCost = BigDecimal.ZERO;

        // 2. Custos: Ano do período selecionado
        LocalDate selectedPeriod = DateUtils.epochToLocalDate(startOfMonth);
        long startOfYear = DateUtils.localDateToEpoch(selectedPeriod.withDayOfYear(1));
        long endOfYear = DateUtils.localDateToEpoch(selectedPeriod.with(TemporalAdjusters.lastDayOfYear()).plusDays(1)) - 1;

        BigDecimal yearlyCost = transactionRepository.getNetVehicleCost(vehicleId, startOfYear, endOfYear);
        if (yearlyCost == null) yearlyCost = BigDecimal.ZERO;

        List<Transactions> periodRefuels = transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, startOfMonth, endOfMonth);

        // 3. Média de consumo exclusiva do período selecionado.
        Double currentAvgKml = calculateAverageKml(vehicleId, startOfMonth, endOfMonth, periodRefuels);

        FuelForecast forecast = calculateFuelForecast(vehicleId, vehicle);
        LastRefuelData lastRefuelData = calculateLastRefuelData(vehicleId, periodRefuels);
        BigDecimal costPerKm = BigDecimal.ZERO;

        BigDecimal kmDrivenInPeriod = calculateKmDrivenInPeriod(vehicleId, startOfMonth, endOfMonth, periodRefuels);
        if (kmDrivenInPeriod.compareTo(BigDecimal.ZERO) > 0) {
            costPerKm = monthlyCost.divide(kmDrivenInPeriod, 2, RoundingMode.HALF_UP);
        }

        return VehicleDashboardDTO.builder()
                .monthlyCost(monthlyCost)
                .yearlyCost(yearlyCost)
                .costPerKm(costPerKm)
                .currentAvgKml(currentAvgKml > 0 ? currentAvgKml : null)
                .remainingKms(forecast.remainingKms())
                .estimatedNextRefuelDate(forecast.estimatedNextRefuelDate())
                .estimatedNextRefuelCost(forecast.estimatedNextRefuelCost())
                .lastRefuelAmount(lastRefuelData.lastRefuelAmount())
                .lastFuelPricePerLiter(lastRefuelData.lastFuelPricePerLiter())
                .lastRefuelDistanceKm(lastRefuelData.lastRefuelDistanceKm())
                .lastRefuelKml(lastRefuelData.lastRefuelKml())
                .lastRefuelFuelType(lastRefuelData.lastRefuelFuelType())
                .build();
    }

    private Double calculateAverageKml(UUID vehicleId, Long start, Long end, List<Transactions> refuels) {
        double kmFromRefuels = 0.0;
        double litersFromRefuels = 0.0;
        for (Transactions refuel : refuels) {
            if (refuel.getEfficiency() != null && refuel.getEfficiency() > 0
                    && refuel.getLiters() != null && refuel.getLiters() > 0) {
                kmFromRefuels += refuel.getEfficiency() * refuel.getLiters();
                litersFromRefuels += refuel.getLiters();
            }
        }
        if (litersFromRefuels > 0) {
            return BigDecimal.valueOf(kmFromRefuels / litersFromRefuels)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        List<VehicleLog> logs = logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end);
        Optional<Double> dashboardAverage = logs.stream()
                .map(VehicleLog::getDashboardKml)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst();
        if (dashboardAverage.isPresent()) {
            return BigDecimal.valueOf(dashboardAverage.get()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        return 0.0;
    }

    private LastRefuelData calculateLastRefuelData(UUID vehicleId, List<Transactions> periodRefuels) {
        Optional<Transactions> lastRefuelOpt = periodRefuels.stream()
                .filter(this::isValidRefuelForLastData)
                .max(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L));

        if (lastRefuelOpt.isEmpty()) {
            return LastRefuelData.empty();
        }

        Transactions lastRefuel = lastRefuelOpt.get();
        BigDecimal lastFuelPricePerLiter = lastRefuel.getAmount()
                .divide(BigDecimal.valueOf(lastRefuel.getLiters()), 2, RoundingMode.HALF_UP);

        Optional<Transactions> previousRefuelOpt = findPreviousRefuelInPeriod(periodRefuels, lastRefuel)
                .or(() -> transactionRepository.findPreviousValidRefuelsByVehicleBeforeDate(vehicleId, lastRefuel.getDate())
                        .stream()
                        .filter(this::isValidRefuelForPreviousDistance)
                        .findFirst());

        if (previousRefuelOpt.isEmpty() || previousRefuelOpt.get().getCurrentOdometer() == null) {
            return new LastRefuelData(lastRefuel.getAmount(), lastFuelPricePerLiter, null, null, lastRefuel.getFuelType());
        }

        BigDecimal distance = lastRefuel.getCurrentOdometer().subtract(previousRefuelOpt.get().getCurrentOdometer());
        if (distance.compareTo(BigDecimal.ZERO) <= 0) {
            return new LastRefuelData(lastRefuel.getAmount(), lastFuelPricePerLiter, null, null, lastRefuel.getFuelType());
        }

        double distanceKm = BigDecimal.valueOf(distance.doubleValue())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        double kml = distance.divide(BigDecimal.valueOf(lastRefuel.getLiters()), 2, RoundingMode.HALF_UP)
                .doubleValue();

        return new LastRefuelData(lastRefuel.getAmount(), lastFuelPricePerLiter, distanceKm, kml, lastRefuel.getFuelType());
    }

    private Optional<Transactions> findPreviousRefuelInPeriod(List<Transactions> periodRefuels, Transactions lastRefuel) {
        return periodRefuels.stream()
                .filter(this::isValidRefuelForPreviousDistance)
                .filter(refuel -> !refuel.getId().equals(lastRefuel.getId()))
                .filter(refuel -> isBefore(refuel, lastRefuel))
                .max(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L));
    }

    private boolean isBefore(Transactions candidate, Transactions reference) {
        int dateComparison = candidate.getDate().compareTo(reference.getDate());
        if (dateComparison < 0) {
            return true;
        }
        if (dateComparison > 0) {
            return false;
        }
        long candidateCreatedAt = candidate.getCreatedAt() != null ? candidate.getCreatedAt() : 0L;
        long referenceCreatedAt = reference.getCreatedAt() != null ? reference.getCreatedAt() : 0L;
        return candidateCreatedAt < referenceCreatedAt;
    }

    private boolean isValidRefuelForLastData(Transactions refuel) {
        return refuel.getLiters() != null && refuel.getLiters() > 0
                && refuel.getAmount() != null && refuel.getAmount().compareTo(BigDecimal.ZERO) > 0
                && refuel.getCurrentOdometer() != null && refuel.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isValidRefuelForPreviousDistance(Transactions refuel) {
        return refuel.getCurrentOdometer() != null && refuel.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0;
    }

    private FuelForecast calculateFuelForecast(UUID vehicleId, Vehicle vehicle) {
        long now = DateUtils.getEpochNow();
        List<Transactions> validRefuels = transactionRepository.findValidRefuelsByVehicleUpToDate(vehicleId, now);
        if (validRefuels.isEmpty()) {
            return FuelForecast.empty();
        }

        Transactions lastRefuel = validRefuels.get(0);
        double litersPurchased = lastRefuel.getLiters();
        double kmlForForecast = resolveForecastKml(lastRefuel, vehicle);
        if (kmlForForecast <= 0 || litersPurchased <= 0 || lastRefuel.getCurrentOdometer() == null || vehicle.getCurrentOdometer() == null) {
            return FuelForecast.empty();
        }

        double kmDrivenSinceRefuel = vehicle.getCurrentOdometer().subtract(lastRefuel.getCurrentOdometer()).doubleValue();
        if (kmDrivenSinceRefuel < 0) {
            return FuelForecast.empty();
        }

        double remainingKms = (litersPurchased * kmlForForecast) - kmDrivenSinceRefuel;
        if (remainingKms <= 0) {
            return FuelForecast.empty();
        }

        Double dailyKmAverage = calculateDailyKmAverage(vehicleId, vehicle, now);
        if (dailyKmAverage == null || dailyKmAverage <= 0) {
            return FuelForecast.withRemainingKms(remainingKms);
        }

        long daysLeft = Math.max(1L, (long) Math.ceil(remainingKms / dailyKmAverage));
        long estimatedDate = now + (daysLeft * 24L * 60L * 60L * 1000L);
        if (estimatedDate < now) {
            return FuelForecast.withRemainingKms(remainingKms);
        }
        return new FuelForecast(remainingKms, estimatedDate, calculateEstimatedRefuelCost(lastRefuel, vehicle));
    }

    private double resolveForecastKml(Transactions lastRefuel, Vehicle vehicle) {
        if (lastRefuel.getEfficiency() != null && lastRefuel.getEfficiency() > 0) {
            return lastRefuel.getEfficiency();
        }
        double avgGasoline = vehicle.getAvgKmPerLiterGasoline() != null ? vehicle.getAvgKmPerLiterGasoline() : 0.0;
        double avgEthanol = vehicle.getAvgKmPerLiterEthanol() != null ? vehicle.getAvgKmPerLiterEthanol() : 0.0;
        return Math.max(avgGasoline, avgEthanol);
    }

    private Double calculateDailyKmAverage(UUID vehicleId, Vehicle vehicle, long now) {
        Optional<VehicleLog> firstLogOpt = logRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(vehicleId, now);
        if (firstLogOpt.isEmpty()) {
            return null;
        }

        VehicleLog firstLog = firstLogOpt.get();
        long daysSinceFirstLog = (now - firstLog.getDate()) / (24L * 60L * 60L * 1000L);
        if (daysSinceFirstLog <= 0 || firstLog.getOdometerReading() == null || vehicle.getCurrentOdometer() == null) {
            return null;
        }

        double totalKmDrivenInApp = vehicle.getCurrentOdometer().subtract(firstLog.getOdometerReading()).doubleValue();
        return totalKmDrivenInApp > 0 ? totalKmDrivenInApp / daysSinceFirstLog : null;
    }

    private BigDecimal calculateEstimatedRefuelCost(Transactions lastRefuel, Vehicle vehicle) {
        if (lastRefuel.getLiters() == null || lastRefuel.getLiters() <= 0 || lastRefuel.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal pricePerLiter = lastRefuel.getAmount().divide(BigDecimal.valueOf(lastRefuel.getLiters()), 2, RoundingMode.HALF_UP);
        Double capacityToFill = vehicle.getTankCapacity() != null ? vehicle.getTankCapacity() : lastRefuel.getLiters();
        return pricePerLiter.multiply(BigDecimal.valueOf(capacityToFill));
    }

    private BigDecimal calculateKmDrivenInPeriod(UUID vehicleId, Long start, Long end, List<Transactions> periodRefuels) {
        List<OdometerPoint> odometerReadings = new ArrayList<>();
        logRepository.findByVehicleIdAndDateBetweenOrderByDateAsc(vehicleId, start, end).stream()
                .filter(log -> log.getOdometerReading() != null && log.getOdometerReading().compareTo(BigDecimal.ZERO) > 0)
                .map(log -> new OdometerPoint(log.getDate(), log.getOdometerReading()))
                .forEach(odometerReadings::add);

        periodRefuels.stream()
                .filter(transaction -> transaction.getCurrentOdometer() != null && transaction.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0)
                .map(transaction -> new OdometerPoint(transaction.getDate(), transaction.getCurrentOdometer()))
                .forEach(odometerReadings::add);

        if (odometerReadings.size() < 2) {
            return BigDecimal.ZERO;
        }

        odometerReadings.sort(Comparator.comparing(OdometerPoint::date));
        BigDecimal first = odometerReadings.get(0).odometer();
        BigDecimal last = odometerReadings.get(odometerReadings.size() - 1).odometer();
        BigDecimal distance = last.subtract(first);
        return distance.compareTo(BigDecimal.ZERO) > 0 ? distance : BigDecimal.ZERO;
    }

    private record OdometerPoint(Long date, BigDecimal odometer) {
    }

    private record LastRefuelData(
            BigDecimal lastRefuelAmount,
            BigDecimal lastFuelPricePerLiter,
            Double lastRefuelDistanceKm,
            Double lastRefuelKml,
            FuelType lastRefuelFuelType) {
        private static LastRefuelData empty() {
            return new LastRefuelData(null, null, null, null, null);
        }
    }

    private record FuelForecast(Double remainingKms, Long estimatedNextRefuelDate, BigDecimal estimatedNextRefuelCost) {
        private static FuelForecast empty() {
            return new FuelForecast(null, null, BigDecimal.ZERO);
        }

        private static FuelForecast withRemainingKms(Double remainingKms) {
            return new FuelForecast(remainingKms, null, BigDecimal.ZERO);
        }
    }
}
