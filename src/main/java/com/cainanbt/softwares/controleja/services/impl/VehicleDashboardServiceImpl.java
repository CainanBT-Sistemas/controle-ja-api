package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
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
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String LOW_CONFIDENCE = "LOW";
    private static final String MEDIUM_CONFIDENCE = "MEDIUM";
    private static final String HIGH_CONFIDENCE = "HIGH";

    private final VehicleDomainValidator vehicleDomainValidator = new VehicleDomainValidator();
    private final DashboardPeriodValidator periodValidator = new DashboardPeriodValidator();

    private final VehicleService vehicleService;
    private final TransactionRepository transactionRepository;

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

        Double currentAvgKml = calculateAverageKml(vehicleId, periodRefuels, vehicle);

        long now = DateUtils.getEpochNow();
        YearMonth selectedMonth = YearMonth.from(selectedPeriod);
        YearMonth currentMonth = YearMonth.from(DateUtils.epochToLocalDate(now));
        FuelForecast forecast = selectedMonth.isBefore(currentMonth)
                ? FuelForecast.empty()
                : calculateFuelForecast(vehicleId, vehicle, now);
        VehicleCostForecast costForecast = calculateVehicleCostForecast(vehicleId, forecast, now, selectedMonth);
        BigDecimal estimatedNextCost = costForecast.nextMonthEstimatedCost();
        ForecastContract forecastContract = buildForecastContract(forecast, costForecast);
        LastRefuelData lastRefuelData = calculateLastRefuelData(vehicleId, periodRefuels, vehicle);
        BigDecimal costPerKm = BigDecimal.ZERO;

        BigDecimal kmDrivenInPeriod = calculateKmDrivenInPeriod(periodRefuels);
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
     * Calcula média KM/L do período usando somente abastecimentos confiáveis.
     */
    private Double calculateAverageKml(UUID vehicleId, List<Transactions> refuels, Vehicle vehicle) {
        if (refuels.size() == 1) {
            Transactions currentRefuel = refuels.get(0);
            Optional<Transactions> previousRefuel = transactionRepository
                    .findPreviousValidRefuelsByVehicleBeforeDate(vehicleId, currentRefuel.getDate())
                    .stream()
                    .filter(this::isValidRefuelForPreviousDistance)
                    .findFirst();
            if (previousRefuel.isPresent()
                    && currentRefuel.getLiters() != null
                    && currentRefuel.getLiters() > 0) {
                BigDecimal distance = currentRefuel.getCurrentOdometer()
                        .subtract(previousRefuel.get().getCurrentOdometer());
                if (distance.compareTo(BigDecimal.ZERO) > 0) {
                    double calculatedKml = distance.doubleValue() / currentRefuel.getLiters();
                    if (isPlausibleKml(calculatedKml)) {
                        return BigDecimal.valueOf(calculatedKml)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue();
                    }
                }
            }
        }

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
        List<Transactions> refuelHistory = transactionRepository.findValidRefuelsByVehicleUpToDate(vehicleId, now).stream()
                .filter(refuel -> isValidRefuelForLastData(refuel, vehicle))
                .filter(refuel -> refuel.getDate() != null)
                .toList();
        if (refuelHistory.isEmpty()) {
            return FuelForecast.empty();
        }

        Transactions lastRefuel = refuelHistory.get(0);
        Transactions previousRefuel = refuelHistory.size() > 1 ? refuelHistory.get(1) : null;
        double litersPurchased = lastRefuel.getLiters();
        double kmlForForecast = resolveForecastKml(lastRefuel, previousRefuel, vehicle);
        if (kmlForForecast <= 0 || litersPurchased <= 0 || lastRefuel.getCurrentOdometer() == null || vehicle.getCurrentOdometer() == null) {
            return FuelForecast.empty();
        }

        double kmDrivenSinceRefuel = vehicle.getCurrentOdometer().subtract(lastRefuel.getCurrentOdometer()).doubleValue();
        if (kmDrivenSinceRefuel < 0) {
            return FuelForecast.empty();
        }

        double remainingKms = (litersPurchased * kmlForForecast) - kmDrivenSinceRefuel;
        Double estimatedLiters = estimateRefuelLiters(vehicle, lastRefuel);
        if (remainingKms <= 0) {
            return new FuelForecast(
                    0.0,
                    now,
                    calculateEstimatedRefuelCost(refuelHistory, now, estimatedLiters),
                    estimatedLiters,
                    lastRefuel.getFuelType(),
                    confidenceForRefuels(refuelHistory)
            );
        }

        Double dailyKmAverage = calculateDailyKmAverage(refuelHistory);
        if (dailyKmAverage == null || dailyKmAverage <= 0) {
            return FuelForecast.withRemainingKms(remainingKms, estimatedLiters, lastRefuel.getFuelType(), confidenceForRefuels(refuelHistory));
        }

        long daysLeft = Math.max(1L, (long) Math.ceil(remainingKms / dailyKmAverage));
        long estimatedDate = now + (daysLeft * DAY_IN_MILLIS);
        if (estimatedDate < now) {
            return FuelForecast.withRemainingKms(remainingKms, estimateRefuelLiters(vehicle, lastRefuel), lastRefuel.getFuelType(), confidenceForRefuels(refuelHistory));
        }

        BigDecimal estimatedRefuelCost = calculateEstimatedRefuelCost(refuelHistory, now, estimatedLiters);
        return new FuelForecast(
                remainingKms,
                estimatedDate,
                estimatedRefuelCost,
                estimatedLiters,
                lastRefuel.getFuelType(),
                confidenceForRefuels(refuelHistory)
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
    private double resolveForecastKml(Transactions lastRefuel, Transactions previousRefuel, Vehicle vehicle) {
        if (previousRefuel != null
                && previousRefuel.getCurrentOdometer() != null
                && lastRefuel.getCurrentOdometer() != null
                && lastRefuel.getLiters() != null
                && lastRefuel.getLiters() > 0) {
            BigDecimal distance = lastRefuel.getCurrentOdometer().subtract(previousRefuel.getCurrentOdometer());
            if (distance.compareTo(BigDecimal.ZERO) > 0) {
                double calculatedKml = distance.doubleValue() / lastRefuel.getLiters();
                if (isPlausibleKml(calculatedKml)) {
                    return calculatedKml;
                }
            }
        }
        if (isPlausibleKml(lastRefuel.getEfficiency())) {
            return lastRefuel.getEfficiency();
        }
        double avgGasoline = vehicle.getAvgKmPerLiterGasoline() != null ? vehicle.getAvgKmPerLiterGasoline() : 0.0;
        double avgEthanol = vehicle.getAvgKmPerLiterEthanol() != null ? vehicle.getAvgKmPerLiterEthanol() : 0.0;
        double vehicleAverage = Math.max(avgGasoline, avgEthanol);
        return isPlausibleKml(vehicleAverage) ? vehicleAverage : 0.0;
    }

    /**
     * Calcula média diária de km entre abastecimentos confiáveis.
     */
    private Double calculateDailyKmAverage(List<Transactions> validRefuels) {
        return calculateDailyKmAverageFromRefuels(validRefuels);
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
    private VehicleCostForecast calculateVehicleCostForecast(
            UUID vehicleId,
            FuelForecast fuelForecast,
            long now,
            YearMonth selectedMonth) {
        YearMonth currentMonth = YearMonth.from(DateUtils.epochToLocalDate(now));
        if (selectedMonth.isBefore(currentMonth)) {
            return VehicleCostForecast.empty();
        }

        HistoricalCostAverage historicalAverage = calculateHistoricalVehicleCostAverage(vehicleId, currentMonth);
        YearMonth targetMonth = selectedMonth.plusMonths(1);
        LocalDate targetStartDate = targetMonth.atDay(1);
        long targetStart = DateUtils.localDateToEpoch(targetStartDate);
        long targetEnd = DateUtils.localDateToEpoch(targetStartDate.plusMonths(1)) - 1;
        BigDecimal scheduledCost = calculateVehicleCost(vehicleId, targetStart, targetEnd);
        List<Long> targetMonthRefuelDates = predictedRefuelDateForMonth(fuelForecast, targetMonth, now);
        BigDecimal refuelEstimate = fuelForecast.estimatedNextRefuelCost()
                .multiply(BigDecimal.valueOf(targetMonthRefuelDates.size()));
        BigDecimal estimatedCost = chooseMonthlyForecastCost(
                scheduledCost.add(refuelEstimate),
                historicalAverage.average()
        );
        List<VehicleDashboardDTO.VehicleFuturePredictionItemDTO> items =
                buildFuturePredictionItems(targetMonthRefuelDates, fuelForecast);
        List<Long> selectedMonthRefuelDates = predictedRefuelDateForMonth(fuelForecast, selectedMonth, now);

        if (estimatedCost.compareTo(BigDecimal.ZERO) <= 0 && items.isEmpty() && selectedMonthRefuelDates.isEmpty()) {
            return VehicleCostForecast.empty();
        }
        String confidence = confidenceForMonthlyForecast(
                scheduledCost,
                historicalAverage.monthsWithCost(),
                targetMonthRefuelDates.size()
        );
        VehicleDashboardDTO.VehicleFuturePredictionDTO prediction =
                VehicleDashboardDTO.VehicleFuturePredictionDTO.builder()
                        .month(targetMonth.toString())
                        .estimatedCost(estimatedCost)
                        .estimatedRefuels(targetMonthRefuelDates.size())
                        .confidence(confidence)
                        .items(items)
                        .build();

        List<VehicleDashboardDTO.VehicleFuturePredictionDTO> futurePredictions =
                buildVisibleFuturePredictions(selectedMonth, fuelForecast, prediction, selectedMonthRefuelDates);
        return new VehicleCostForecast(estimatedCost, confidence, futurePredictions);
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
     * Retorna no máximo uma previsão de abastecimento quando a data pertence ao mês informado.
     */
    private List<Long> predictedRefuelDateForMonth(FuelForecast fuelForecast, YearMonth month, long now) {
        Long estimatedDate = fuelForecast.estimatedNextRefuelDate();
        if (estimatedDate == null || estimatedDate < now) {
            return List.of();
        }
        YearMonth estimatedMonth = YearMonth.from(DateUtils.epochToLocalDate(estimatedDate));
        return estimatedMonth.equals(month) ? List.of(estimatedDate) : List.of();
    }

    /**
     * Exibe a previsão no mês selecionado quando ela ainda ocorrer nele; caso contrário,
     * mantém somente o consolidado do mês seguinte.
     */
    private List<VehicleDashboardDTO.VehicleFuturePredictionDTO> buildVisibleFuturePredictions(
            YearMonth selectedMonth,
            FuelForecast fuelForecast,
            VehicleDashboardDTO.VehicleFuturePredictionDTO nextMonthPrediction,
            List<Long> selectedMonthRefuelDates) {
        if (selectedMonthRefuelDates.isEmpty()) {
            return List.of(nextMonthPrediction);
        }

        List<VehicleDashboardDTO.VehicleFuturePredictionItemDTO> items =
                buildFuturePredictionItems(selectedMonthRefuelDates, fuelForecast);
        VehicleDashboardDTO.VehicleFuturePredictionDTO selectedMonthPrediction =
                VehicleDashboardDTO.VehicleFuturePredictionDTO.builder()
                        .month(selectedMonth.toString())
                        .estimatedCost(fuelForecast.estimatedNextRefuelCost())
                        .estimatedRefuels(1)
                        .confidence(fuelForecast.confidence())
                        .items(items)
                        .build();
        return List.of(selectedMonthPrediction);
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
     * Calcula km rodados no período usando os odômetros dos abastecimentos.
     */
    private BigDecimal calculateKmDrivenInPeriod(List<Transactions> periodRefuels) {
        List<OdometerPoint> odometerReadings = periodRefuels.stream()
                .filter(transaction -> transaction.getCurrentOdometer() != null && transaction.getCurrentOdometer().compareTo(BigDecimal.ZERO) > 0)
                .map(transaction -> new OdometerPoint(transaction.getDate(), transaction.getCurrentOdometer()))
                .sorted(Comparator.comparing(OdometerPoint::date))
                .toList();

        if (odometerReadings.size() < 2) {
            return BigDecimal.ZERO;
        }

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
            String confidence) {
        private static FuelForecast empty() {
            return new FuelForecast(null, null, BigDecimal.ZERO, null, null, null);
        }

        private static FuelForecast withRemainingKms(Double remainingKms, Double estimatedLiters, FuelType fuelType, String confidence) {
            return new FuelForecast(remainingKms, null, BigDecimal.ZERO, estimatedLiters, fuelType, confidence);
        }
    }
}
