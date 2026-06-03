package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.VehicleDashboardService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.dashboard.DashboardPeriodValidator;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleDomainValidator;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleDashboardServiceImpl implements VehicleDashboardService {
    private static final double MAX_REFUEL_LITERS_WITHOUT_TANK_CAPACITY = 200.0;
    private static final double TANK_CAPACITY_TOLERANCE_FACTOR = 1.5;
    private static final double MAX_PLAUSIBLE_KML = 100.0;
    private static final int FORECAST_COST_MONTHS = 6;
    private static final int FUTURE_FORECAST_MONTHS = 3;
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String LOW_CONFIDENCE = "LOW";
    private static final String MEDIUM_CONFIDENCE = "MEDIUM";
    private static final String HIGH_CONFIDENCE = "HIGH";

    private final VehicleDomainValidator vehicleDomainValidator = new VehicleDomainValidator();
    private final DashboardPeriodValidator periodValidator = new DashboardPeriodValidator();

    private final VehicleService vehicleService;
    private final TransactionRepository transactionRepository;
    private final VehicleLogRepository logRepository;

    /**
     * Monta o dashboard carregando e autorizando o veículo pelo identificador informado.
     */
    @Override
    public VehicleDashboardDTO getDashboard(UUID vehicleId, Long startOfMonth, Long endOfMonth) {
        Vehicle vehicle = vehicleService.findByIdOrThrow(vehicleId);
        SecurityContextUtils.getUserLogged()
                .ifPresent(user -> vehicleDomainValidator.validateOwner(vehicle, user));
        return getDashboard(vehicle, startOfMonth, endOfMonth);
    }

    /**
     * Monta métricas de custo, consumo, último abastecimento e previsão para o veículo autorizado.
     */
    @Override
    public VehicleDashboardDTO getDashboard(Vehicle vehicle, Long startOfMonth, Long endOfMonth) {
        periodValidator.validate(startOfMonth, endOfMonth);
        if (vehicle == null || vehicle.getId() == null) {
            throw new BadRequestException("Erro", "Veículo inválido para consulta do dashboard.");
        }

        UUID vehicleId = vehicle.getId();
        BigDecimal monthlyCost = calculateVehicleCost(vehicleId, startOfMonth, endOfMonth);

        LocalDate selectedPeriod = DateUtils.epochToLocalDate(startOfMonth);
        long startOfYear = DateUtils.localDateToEpoch(selectedPeriod.withDayOfYear(1));
        long endOfYear = DateUtils.localDateToEpoch(selectedPeriod.with(TemporalAdjusters.lastDayOfYear()).plusDays(1)) - 1;

        BigDecimal yearlyCost = calculateVehicleCost(vehicleId, startOfYear, endOfYear);

        List<Transactions> periodRefuels = transactionRepository.findRefuelsByVehicleAndDateBetween(vehicleId, startOfMonth, endOfMonth);

        Double currentAvgKml = calculateAverageKml(vehicleId, startOfMonth, endOfMonth, periodRefuels, vehicle);

        boolean canExposeFuturePredictions = isCurrentMonthDashboard(startOfMonth);
        long now = DateUtils.getEpochNow();
        FuelForecast forecast = canExposeFuturePredictions ? calculateFuelForecast(vehicleId, vehicle, now) : FuelForecast.empty();
        VehicleCostForecast costForecast = canExposeFuturePredictions
                ? calculateVehicleCostForecast(vehicleId, forecast, now)
                : VehicleCostForecast.empty();
        BigDecimal estimatedNextCost = costForecast.nextMonthEstimatedCost();
        ForecastContract forecastContract = canExposeFuturePredictions
                ? buildForecastContract(forecast, costForecast)
                : ForecastContract.empty();
        LastRefuelData lastRefuelData = calculateLastRefuelData(vehicleId, periodRefuels, vehicle);
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
                .estimatedNextCost(estimatedNextCost)
                .nextMonthEstimatedCost(forecastContract.nextMonthEstimatedCost())
                .nextMonthEstimatedCostConfidence(forecastContract.nextMonthEstimatedCostConfidence())
                .nextRefuelPrediction(forecastContract.nextRefuelPrediction())
                .futurePredictions(forecastContract.futurePredictions())
                .lastRefuelAmount(lastRefuelData.lastRefuelAmount())
                .lastFuelPricePerLiter(lastRefuelData.lastFuelPricePerLiter())
                .lastRefuelDistanceKm(lastRefuelData.lastRefuelDistanceKm())
                .lastRefuelKml(lastRefuelData.lastRefuelKml())
                .lastRefuelFuelType(lastRefuelData.lastRefuelFuelType())
                .build();
    }

    /**
     * Libera previsões somente para o dashboard do mês atual, evitando mostrar projeções em meses históricos.
     */
    private boolean isCurrentMonthDashboard(long startOfMonth) {
        YearMonth selectedMonth = YearMonth.from(DateUtils.epochToLocalDate(startOfMonth));
        YearMonth currentMonth = YearMonth.from(DateUtils.epochToLocalDate(DateUtils.getEpochNow()));
        return selectedMonth.equals(currentMonth);
    }

    /**
     * Converte a previsão interna para o contrato novo sem remover os campos legados usados pelo app.
     */
    private ForecastContract buildForecastContract(FuelForecast forecast, VehicleCostForecast costForecast) {
        BigDecimal safeEstimatedNextCost = costForecast.nextMonthEstimatedCost() != null ? costForecast.nextMonthEstimatedCost() : BigDecimal.ZERO;
        BigDecimal safeRefuelCost = forecast.estimatedNextRefuelCost() != null ? forecast.estimatedNextRefuelCost() : BigDecimal.ZERO;

        VehicleDashboardDTO.VehicleRefuelPredictionDTO refuelPrediction = null;
        if (forecast.estimatedNextRefuelDate() != null || safeRefuelCost.compareTo(BigDecimal.ZERO) > 0) {
            refuelPrediction = VehicleDashboardDTO.VehicleRefuelPredictionDTO.builder()
                    .estimatedDate(forecast.estimatedNextRefuelDate())
                    .estimatedCost(safeRefuelCost)
                    .estimatedLiters(forecast.estimatedLiters())
                    .fuelType(forecast.fuelType())
                    .confidence(forecast.confidence())
                    .basis("Historico recente de abastecimentos, odometro atual e media diaria de uso.")
                    .build();
        }

        return new ForecastContract(
                safeEstimatedNextCost,
                costForecast.nextMonthConfidence(),
                refuelPrediction,
                costForecast.futurePredictions()
        );
    }

    /**
     * Calcula média KM/L do período usando abastecimentos confiáveis ou diário de bordo como fallback.
     */
    private Double calculateAverageKml(UUID vehicleId, Long start, Long end, List<Transactions> refuels, Vehicle vehicle) {
        double kmFromRefuels = 0.0;
        double litersFromRefuels = 0.0;
        for (Transactions refuel : refuels) {
            if (isValidRefuelForAverage(refuel, vehicle)) {
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

    /**
     * Resolve os dados do último abastecimento confiável dentro do período.
     */
    private LastRefuelData calculateLastRefuelData(UUID vehicleId, List<Transactions> periodRefuels, Vehicle vehicle) {
        Optional<Transactions> lastRefuelOpt = periodRefuels.stream()
                .filter(refuel -> isValidRefuelForLastData(refuel, vehicle))
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

        double calculatedKml = distance.doubleValue() / lastRefuel.getLiters();
        if (!isPlausibleKml(calculatedKml)) {
            return new LastRefuelData(lastRefuel.getAmount(), lastFuelPricePerLiter, null, null, lastRefuel.getFuelType());
        }

        double distanceKm = BigDecimal.valueOf(distance.doubleValue())
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        double kml = BigDecimal.valueOf(calculatedKml)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        return new LastRefuelData(lastRefuel.getAmount(), lastFuelPricePerLiter, distanceKm, kml, lastRefuel.getFuelType());
    }

    /**
     * Localiza o abastecimento anterior dentro do mesmo período usando data e createdAt como desempate.
     */
    private Optional<Transactions> findPreviousRefuelInPeriod(List<Transactions> periodRefuels, Transactions lastRefuel) {
        return periodRefuels.stream()
                .filter(this::isValidRefuelForPreviousDistance)
                .filter(refuel -> !refuel.getId().equals(lastRefuel.getId()))
                .filter(refuel -> isBefore(refuel, lastRefuel))
                .max(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L));
    }

    /**
     * Compara duas transações respeitando data e ordem de criação.
     */
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

    /**
     * Valida se o abastecimento pode contribuir para média do período.
     */
    private boolean isValidRefuelForAverage(Transactions refuel, Vehicle vehicle) {
        return isValidRefuelForLastData(refuel, vehicle)
                && refuel.getEfficiency() != null
                && isPlausibleKml(refuel.getEfficiency());
    }

    /**
     * Valida campos mínimos do abastecimento para dados financeiros e odômetro.
     */
    private boolean isValidRefuelForLastData(Transactions refuel, Vehicle vehicle) {
        return refuel.getLiters() != null && refuel.getLiters() > 0
                && isPlausibleLiters(refuel.getLiters(), vehicle)
                && refuel.getAmount() != null && refuel.getAmount().compareTo(BigDecimal.ZERO) > 0
                && refuel.getCurrentOdometer() != null && refuel.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Confirma que há odômetro válido para calcular distância entre abastecimentos.
     */
    private boolean isValidRefuelForPreviousDistance(Transactions refuel) {
        return refuel.getCurrentOdometer() != null && refuel.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Bloqueia litros zerados, negativos ou incompatíveis com a capacidade do tanque.
     */
    private boolean isPlausibleLiters(Double liters, Vehicle vehicle) {
        if (liters == null || liters <= 0) {
            return false;
        }
        if (vehicle.getTankCapacity() != null && vehicle.getTankCapacity() > 0) {
            return liters <= vehicle.getTankCapacity() * TANK_CAPACITY_TOLERANCE_FACTOR;
        }
        return liters <= MAX_REFUEL_LITERS_WITHOUT_TANK_CAPACITY;
    }

    /**
     * Limita KM/L a uma faixa positiva e plausível para evitar outliers no dashboard.
     */
    private boolean isPlausibleKml(Double kml) {
        return kml != null && kml > 0 && kml <= MAX_PLAUSIBLE_KML;
    }

    /**
     * Calcula autonomia restante, próxima data estimada e custo estimado do próximo abastecimento.
     */
    private FuelForecast calculateFuelForecast(UUID vehicleId, Vehicle vehicle, long now) {
        List<Transactions> validRefuels = transactionRepository.findValidRefuelsByVehicleUpToDate(vehicleId, now).stream()
                .filter(refuel -> isValidRefuelForForecast(refuel, vehicle))
                .toList();
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

        Double dailyKmAverage = calculateDailyKmAverage(vehicleId, vehicle, now, validRefuels);
        if (dailyKmAverage == null || dailyKmAverage <= 0) {
            return FuelForecast.withRemainingKms(remainingKms, estimateRefuelLiters(vehicle, lastRefuel), lastRefuel.getFuelType(), confidenceForRefuels(validRefuels));
        }

        long daysLeft = Math.max(1L, (long) Math.ceil(remainingKms / dailyKmAverage));
        long estimatedDate = now + (daysLeft * DAY_IN_MILLIS);
        if (estimatedDate < now) {
            return FuelForecast.withRemainingKms(remainingKms, estimateRefuelLiters(vehicle, lastRefuel), lastRefuel.getFuelType(), confidenceForRefuels(validRefuels));
        }

        Double estimatedLiters = estimateRefuelLiters(vehicle, lastRefuel);
        BigDecimal estimatedRefuelCost = calculateEstimatedRefuelCost(validRefuels, now, estimatedLiters);
        return new FuelForecast(
                remainingKms,
                estimatedDate,
                estimatedRefuelCost,
                estimatedLiters,
                lastRefuel.getFuelType(),
                confidenceForRefuels(validRefuels),
                calculateAverageDaysBetweenRefuels(validRefuels)
        );
    }

    /**
     * Estima o volume do próximo abastecimento pela capacidade do tanque ou pelo último volume confiável.
     */
    private Double estimateRefuelLiters(Vehicle vehicle, Transactions lastRefuel) {
        if (vehicle.getTankCapacity() != null && vehicle.getTankCapacity() > 0) {
            return vehicle.getTankCapacity();
        }
        return lastRefuel.getLiters() != null && lastRefuel.getLiters() > 0 ? lastRefuel.getLiters() : null;
    }

    /**
     * Decide qual KM/L usar na previsão: abastecimento atual ou média cadastrada no veículo.
     */
    private double resolveForecastKml(Transactions lastRefuel, Vehicle vehicle) {
        if (isPlausibleKml(lastRefuel.getEfficiency())) {
            return lastRefuel.getEfficiency();
        }
        double avgGasoline = vehicle.getAvgKmPerLiterGasoline() != null ? vehicle.getAvgKmPerLiterGasoline() : 0.0;
        double avgEthanol = vehicle.getAvgKmPerLiterEthanol() != null ? vehicle.getAvgKmPerLiterEthanol() : 0.0;
        double vehicleAverage = Math.max(avgGasoline, avgEthanol);
        return isPlausibleKml(vehicleAverage) ? vehicleAverage : 0.0;
    }

    /**
     * Valida se o abastecimento pode alimentar a previsão de autonomia.
     */
    private boolean isValidRefuelForForecast(Transactions refuel, Vehicle vehicle) {
        return isValidRefuelForLastData(refuel, vehicle)
                && refuel.getDate() != null
                && isPlausibleKml(resolveForecastKml(refuel, vehicle));
    }

    /**
     * Calcula média diária de km usando abastecimentos ou o primeiro diário de bordo disponível.
     */
    private Double calculateDailyKmAverage(UUID vehicleId, Vehicle vehicle, long now, List<Transactions> validRefuels) {
        Double refuelAverage = calculateDailyKmAverageFromRefuels(validRefuels);
        if (refuelAverage != null && refuelAverage > 0) {
            return refuelAverage;
        }

        Optional<VehicleLog> firstLogOpt = logRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(vehicleId, now);
        if (firstLogOpt.isEmpty()) {
            return null;
        }

        VehicleLog firstLog = firstLogOpt.get();
        long daysSinceFirstLog = (now - firstLog.getDate()) / DAY_IN_MILLIS;
        if (daysSinceFirstLog <= 0 || firstLog.getOdometerReading() == null || vehicle.getCurrentOdometer() == null) {
            return null;
        }

        double totalKmDrivenInApp = vehicle.getCurrentOdometer().subtract(firstLog.getOdometerReading()).doubleValue();
        return totalKmDrivenInApp > 0 ? totalKmDrivenInApp / daysSinceFirstLog : null;
    }

    /**
     * Calcula km/dia entre o primeiro e o último abastecimento confiável.
     */
    private Double calculateDailyKmAverageFromRefuels(List<Transactions> validRefuels) {
        List<Transactions> orderedRefuels = validRefuels.stream()
                .filter(refuel -> refuel.getDate() != null)
                .filter(refuel -> refuel.getCurrentOdometer() != null && refuel.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L))
                .toList();

        if (orderedRefuels.size() < 2) {
            return null;
        }

        Transactions firstRefuel = orderedRefuels.get(0);
        Transactions lastRefuel = orderedRefuels.get(orderedRefuels.size() - 1);
        long daysBetween = (lastRefuel.getDate() - firstRefuel.getDate()) / DAY_IN_MILLIS;
        if (daysBetween <= 0) {
            return null;
        }

        BigDecimal distance = lastRefuel.getCurrentOdometer().subtract(firstRefuel.getCurrentOdometer());
        return distance.compareTo(BigDecimal.ZERO) > 0 ? distance.doubleValue() / daysBetween : null;
    }

    /**
     * Calcula custo médio mensal de combustível usando meses com abastecimento nos últimos meses.
     */
    private BigDecimal calculateEstimatedRefuelCost(List<Transactions> validRefuels, long now, Double estimatedLiters) {
        BigDecimal averageLiterPrice = calculateAverageLiterPrice(validRefuels, now);
        if (averageLiterPrice.compareTo(BigDecimal.ZERO) > 0 && estimatedLiters != null && estimatedLiters > 0) {
            return averageLiterPrice
                    .multiply(BigDecimal.valueOf(estimatedLiters))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        long costStart = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(now).minusMonths(FORECAST_COST_MONTHS - 1).withDayOfMonth(1));
        Map<YearMonth, BigDecimal> monthlyFuelCosts = new LinkedHashMap<>();

        for (Transactions refuel : validRefuels) {
            if (refuel.getDate() == null || refuel.getDate() < costStart || refuel.getDate() > now || refuel.getAmount() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(DateUtils.epochToLocalDate(refuel.getDate()));
            monthlyFuelCosts.merge(month, refuel.getAmount(), BigDecimal::add);
        }

        if (monthlyFuelCosts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = monthlyFuelCosts.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(monthlyFuelCosts.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula preço médio por litro recente, ignorando abastecimentos sem valor ou volume confiável.
     */
    private BigDecimal calculateAverageLiterPrice(List<Transactions> validRefuels, long now) {
        long costStart = DateUtils.localDateToEpoch(DateUtils.epochToLocalDate(now).minusMonths(FORECAST_COST_MONTHS - 1).withDayOfMonth(1));
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalLiters = BigDecimal.ZERO;

        for (Transactions refuel : validRefuels) {
            if (refuel.getDate() == null || refuel.getDate() < costStart || refuel.getDate() > now
                    || refuel.getAmount() == null || refuel.getLiters() == null || refuel.getLiters() <= 0) {
                continue;
            }
            totalAmount = totalAmount.add(refuel.getAmount());
            totalLiters = totalLiters.add(BigDecimal.valueOf(refuel.getLiters()));
        }

        if (totalLiters.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(totalLiters, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula intervalo médio entre abastecimentos para projetar recorrência nos próximos meses.
     */
    private Long calculateAverageDaysBetweenRefuels(List<Transactions> validRefuels) {
        List<Transactions> orderedRefuels = validRefuels.stream()
                .filter(refuel -> refuel.getDate() != null)
                .sorted(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L))
                .toList();

        if (orderedRefuels.size() < 2) {
            return null;
        }

        long totalDays = 0;
        int intervals = 0;
        for (int i = 1; i < orderedRefuels.size(); i++) {
            long days = (orderedRefuels.get(i).getDate() - orderedRefuels.get(i - 1).getDate()) / DAY_IN_MILLIS;
            if (days > 0) {
                totalDays += days;
                intervals++;
            }
        }

        if (intervals == 0) {
            return null;
        }
        return Math.max(1L, Math.round((double) totalDays / intervals));
    }

    /**
     * Define confiança da previsão de abastecimento pela quantidade de eventos confiáveis.
     */
    private String confidenceForRefuels(List<Transactions> validRefuels) {
        if (validRefuels.size() >= 4) {
            return HIGH_CONFIDENCE;
        }
        if (validRefuels.size() >= 2) {
            return MEDIUM_CONFIDENCE;
        }
        return LOW_CONFIDENCE;
    }

    /**
     * Projeta custos dos próximos meses usando parcelas/gastos já lançados, média histórica e abastecimentos previstos.
     */
    private VehicleCostForecast calculateVehicleCostForecast(UUID vehicleId, FuelForecast fuelForecast, long now) {
        YearMonth currentMonth = YearMonth.from(DateUtils.epochToLocalDate(now));
        HistoricalCostAverage historicalAverage = calculateHistoricalVehicleCostAverage(vehicleId, currentMonth);
        List<Long> predictedRefuelDates = predictRefuelDates(fuelForecast, currentMonth.plusMonths(FUTURE_FORECAST_MONTHS));
        List<VehicleDashboardDTO.VehicleFuturePredictionDTO> predictions = new ArrayList<>();

        for (int i = 1; i <= FUTURE_FORECAST_MONTHS; i++) {
            YearMonth month = currentMonth.plusMonths(i);
            LocalDate startDate = month.atDay(1);
            long start = DateUtils.localDateToEpoch(startDate);
            long end = DateUtils.localDateToEpoch(startDate.plusMonths(1)) - 1;
            BigDecimal scheduledCost = calculateVehicleCost(vehicleId, start, end);
            List<Long> monthRefuelDates = predictedRefuelDates.stream()
                    .filter(date -> YearMonth.from(DateUtils.epochToLocalDate(date)).equals(month))
                    .toList();
            BigDecimal refuelEstimate = fuelForecast.estimatedNextRefuelCost()
                    .multiply(BigDecimal.valueOf(monthRefuelDates.size()));
            BigDecimal scheduledWithRefuels = scheduledCost.add(refuelEstimate);
            BigDecimal estimatedCost = chooseMonthlyForecastCost(scheduledWithRefuels, historicalAverage.average());
            String confidence = confidenceForMonthlyForecast(scheduledCost, historicalAverage.monthsWithCost(), monthRefuelDates.size());

            List<VehicleDashboardDTO.VehicleFuturePredictionItemDTO> items = buildFuturePredictionItems(monthRefuelDates, fuelForecast);
            if (estimatedCost.compareTo(BigDecimal.ZERO) > 0 || !items.isEmpty()) {
                predictions.add(VehicleDashboardDTO.VehicleFuturePredictionDTO.builder()
                        .month(month.toString())
                        .estimatedCost(estimatedCost)
                        .estimatedRefuels(monthRefuelDates.size())
                        .confidence(confidence)
                        .items(items)
                        .build());
            }
        }

        if (predictions.isEmpty()) {
            return VehicleCostForecast.empty();
        }
        VehicleDashboardDTO.VehicleFuturePredictionDTO nextMonth = predictions.get(0);
        return new VehicleCostForecast(nextMonth.getEstimatedCost(), nextMonth.getConfidence(), predictions);
    }

    /**
     * Calcula média histórica mensal ignorando meses zerados para evitar diluir um histórico ainda curto.
     */
    private HistoricalCostAverage calculateHistoricalVehicleCostAverage(UUID vehicleId, YearMonth currentMonth) {
        BigDecimal total = BigDecimal.ZERO;
        int monthsWithCost = 0;

        for (int i = 0; i < FORECAST_COST_MONTHS; i++) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate startDate = month.atDay(1);
            long start = DateUtils.localDateToEpoch(startDate);
            long end = DateUtils.localDateToEpoch(startDate.plusMonths(1)) - 1;
            BigDecimal monthCost = calculateVehicleCost(vehicleId, start, end);
            if (monthCost.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(monthCost);
                monthsWithCost++;
            }
        }

        if (monthsWithCost == 0) {
            return new HistoricalCostAverage(BigDecimal.ZERO, 0);
        }
        return new HistoricalCostAverage(total.divide(BigDecimal.valueOf(monthsWithCost), 2, RoundingMode.HALF_UP), monthsWithCost);
    }

    /**
     * Projeta datas de abastecimento dentro da janela futura suportada pelo contrato.
     */
    private List<Long> predictRefuelDates(FuelForecast fuelForecast, YearMonth limitMonth) {
        if (fuelForecast.estimatedNextRefuelDate() == null) {
            return List.of();
        }

        long limit = DateUtils.localDateToEpoch(limitMonth.atDay(1));
        List<Long> dates = new ArrayList<>();
        long nextDate = fuelForecast.estimatedNextRefuelDate();
        long interval = fuelForecast.averageDaysBetweenRefuels() != null && fuelForecast.averageDaysBetweenRefuels() > 0
                ? fuelForecast.averageDaysBetweenRefuels() * DAY_IN_MILLIS
                : Long.MAX_VALUE;

        while (nextDate < limit) {
            dates.add(nextDate);
            if (interval == Long.MAX_VALUE) {
                break;
            }
            nextDate += interval;
        }
        return dates;
    }

    /**
     * Escolhe entre custo futuro já lançado e média histórica, usando o maior para não subestimar parcelas conhecidas.
     */
    private BigDecimal chooseMonthlyForecastCost(BigDecimal scheduledWithRefuels, BigDecimal historicalAverage) {
        BigDecimal safeScheduled = valueOrZero(scheduledWithRefuels);
        BigDecimal safeHistory = valueOrZero(historicalAverage);
        return safeScheduled.max(safeHistory);
    }

    /**
     * Classifica a confiança mensal conforme a existência de lançamentos futuros, histórico e abastecimentos previstos.
     */
    private String confidenceForMonthlyForecast(BigDecimal scheduledCost, int historicalMonths, int predictedRefuels) {
        if (scheduledCost.compareTo(BigDecimal.ZERO) > 0 && historicalMonths >= 3) {
            return HIGH_CONFIDENCE;
        }
        if (scheduledCost.compareTo(BigDecimal.ZERO) > 0 || historicalMonths >= 3 || predictedRefuels > 0) {
            return MEDIUM_CONFIDENCE;
        }
        if (historicalMonths > 0) {
            return LOW_CONFIDENCE;
        }
        return null;
    }

    /**
     * Monta itens explicitos de abastecimento para o mes previsto.
     */
    private List<VehicleDashboardDTO.VehicleFuturePredictionItemDTO> buildFuturePredictionItems(List<Long> refuelDates, FuelForecast fuelForecast) {
        if (refuelDates.isEmpty()) {
            return List.of();
        }
        return refuelDates.stream()
                .map(date -> VehicleDashboardDTO.VehicleFuturePredictionItemDTO.builder()
                        .type("REFUEL")
                        .description("Abastecimento previsto")
                        .estimatedDate(date)
                        .estimatedCost(fuelForecast.estimatedNextRefuelCost())
                        .confidence(fuelForecast.confidence())
                        .build())
                .toList();
    }

    /**
     * Soma custos diretos do veículo e parcelas de cartão vencendo no período, evitando contar compras parceladas pelo valor total.
     */
    private BigDecimal calculateVehicleCost(UUID vehicleId, long start, long end) {
        BigDecimal directCost = transactionRepository.getNetVehicleCost(vehicleId, start, end);
        BigDecimal installmentCost = transactionRepository.getNetVehicleInstallmentCost(vehicleId, start, end);
        return valueOrZero(directCost).add(valueOrZero(installmentCost));
    }

    /**
     * Normaliza valores monetários opcionais para evitar nulos em somas de agregações.
     */
    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Calcula km rodados no período combinando leituras de diário e odômetros dos abastecimentos.
     */
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

    private record ForecastContract(
            BigDecimal nextMonthEstimatedCost,
            String nextMonthEstimatedCostConfidence,
            VehicleDashboardDTO.VehicleRefuelPredictionDTO nextRefuelPrediction,
            List<VehicleDashboardDTO.VehicleFuturePredictionDTO> futurePredictions) {
        private static ForecastContract empty() {
            return new ForecastContract(BigDecimal.ZERO, null, null, List.of());
        }
    }

    private record VehicleCostForecast(
            BigDecimal nextMonthEstimatedCost,
            String nextMonthConfidence,
            List<VehicleDashboardDTO.VehicleFuturePredictionDTO> futurePredictions) {
        private static VehicleCostForecast empty() {
            return new VehicleCostForecast(BigDecimal.ZERO, null, List.of());
        }
    }

    private record HistoricalCostAverage(BigDecimal average, int monthsWithCost) {
    }

    private record FuelForecast(
            Double remainingKms,
            Long estimatedNextRefuelDate,
            BigDecimal estimatedNextRefuelCost,
            Double estimatedLiters,
            FuelType fuelType,
            String confidence,
            Long averageDaysBetweenRefuels) {
        private static FuelForecast empty() {
            return new FuelForecast(null, null, BigDecimal.ZERO, null, null, null, null);
        }

        private static FuelForecast withRemainingKms(Double remainingKms, Double estimatedLiters, FuelType fuelType, String confidence) {
            return new FuelForecast(remainingKms, null, BigDecimal.ZERO, estimatedLiters, fuelType, confidence, null);
        }
    }
}
