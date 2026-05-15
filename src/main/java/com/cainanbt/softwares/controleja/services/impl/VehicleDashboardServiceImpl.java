package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.VehicleDashboardDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
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
        BigDecimal monthlyCost = transactionRepository.getTotalExpenseByVehicle(vehicleId, startOfMonth, endOfMonth);
        if (monthlyCost == null) monthlyCost = BigDecimal.ZERO;

        // 2. Custos: Ano atual
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        long startOfYear = DateUtils.localDateToEpoch(today.withDayOfYear(1));
        long endOfYear = DateUtils.localDateToEpoch(today.with(TemporalAdjusters.lastDayOfYear()));

        BigDecimal yearlyCost = transactionRepository.getTotalExpenseByVehicle(vehicleId, startOfYear, endOfYear);
        if (yearlyCost == null) yearlyCost = BigDecimal.ZERO;

        // 3. Média de Consumo e Previsões
        Double avgGasoline = vehicle.getAvgKmPerLiterGasoline() != null ? vehicle.getAvgKmPerLiterGasoline() : 0.0;
        Double avgEthanol = vehicle.getAvgKmPerLiterEthanol() != null ? vehicle.getAvgKmPerLiterEthanol() : 0.0;
        Double currentAvgKml = Math.max(avgGasoline, avgEthanol); // Pega a melhor média que o carro tem registrada

        Double remainingKms = 0.0;
        Long estimatedNextRefuelDate = null;
        BigDecimal estimatedNextRefuelCost = BigDecimal.ZERO;
        BigDecimal costPerKm = BigDecimal.ZERO;

        // Tenta encontrar o histórico do carro para fazer previsões
        Optional<Transactions> lastRefuelOpt = transactionRepository.findFirstByVehicleIdAndFuelTypeIsNotNullAndDeletedAtIsNullOrderByDateDesc(vehicleId);
        Optional<VehicleLog> firstLogOpt = logRepository.findFirstByVehicleIdOrderByDateAsc(vehicleId);

        if (lastRefuelOpt.isPresent() && currentAvgKml > 0) {
            Transactions lastRefuel = lastRefuelOpt.get();
            Double litersPurchased = lastRefuel.getLiters() != null ? lastRefuel.getLiters() : 0.0;

            // Qual era o odômetro na hora de abastecer?
            BigDecimal refuelOdometer = lastRefuel.getCurrentOdometer() != null ? lastRefuel.getCurrentOdometer() : vehicle.getCurrentOdometer();

            // Quanto andou desde o abastecimento?
            double kmDrivenSinceRefuel = vehicle.getCurrentOdometer().subtract(refuelOdometer).doubleValue();

            // Autonomia teórica do abastecimento
            double theoreticalAutonomy = litersPurchased * currentAvgKml;

            remainingKms = theoreticalAutonomy - kmDrivenSinceRefuel;
            if (remainingKms < 0) remainingKms = 0.0; // Já passou da hora de abastecer

            // Calcula a média de KM que ele anda por dia (Baseado no histórico do app, ou chuta 15km/dia se for novo)
            double dailyKmAverage = 15.0;
            if (firstLogOpt.isPresent()) {
                VehicleLog firstLog = firstLogOpt.get();
                long daysSinceFirstLog = (DateUtils.getEpochNow() - firstLog.getDate()) / (1000 * 60 * 60 * 24);
                if (daysSinceFirstLog > 0) {
                    double totalKmDrivenInApp = vehicle.getCurrentOdometer().subtract(firstLog.getOdometerReading()).doubleValue();
                    dailyKmAverage = totalKmDrivenInApp / daysSinceFirstLog;
                }
            }

            // Calcula dias restantes e data prevista
            if (dailyKmAverage > 0) {
                int daysLeft = (int) (remainingKms / dailyKmAverage);
                estimatedNextRefuelDate = DateUtils.getEpochNow() + ((long) daysLeft * 24 * 60 * 60 * 1000);
            }

            // Estima o custo com base no valor por litro do último abastecimento
            if (litersPurchased > 0) {
                BigDecimal pricePerLiter = lastRefuel.getAmount().divide(BigDecimal.valueOf(litersPurchased), 2, RoundingMode.HALF_UP);

                // Se ele cadastrou capacidade do tanque, prevê o valor para o "Tanque Cheio"
                Double capacityToFill = vehicle.getTankCapacity() != null ? vehicle.getTankCapacity() : litersPurchased;
                estimatedNextRefuelCost = pricePerLiter.multiply(BigDecimal.valueOf(capacityToFill));
            }
        }

        // Calcula Custo Geral por KM (Total Gasto no ano / Total KM andado no ano)
        if (firstLogOpt.isPresent()) {
            VehicleLog firstLog = firstLogOpt.get();
            BigDecimal totalKmApp = vehicle.getCurrentOdometer().subtract(firstLog.getOdometerReading());
            if (totalKmApp.compareTo(BigDecimal.ZERO) > 0) {
                // Pega todo o custo desde o primeiro log
                BigDecimal totalCostApp = transactionRepository.getTotalExpenseByVehicle(vehicleId, firstLog.getDate(), DateUtils.getEpochNow());
                if (totalCostApp != null) {
                    costPerKm = totalCostApp.divide(totalKmApp, 2, RoundingMode.HALF_UP);
                }
            }
        }

        return VehicleDashboardDTO.builder()
                .monthlyCost(monthlyCost)
                .yearlyCost(yearlyCost)
                .costPerKm(costPerKm)
                .currentAvgKml(currentAvgKml > 0 ? currentAvgKml : null)
                .remainingKms(remainingKms > 0 ? remainingKms : null)
                .estimatedNextRefuelDate(estimatedNextRefuelDate)
                .estimatedNextRefuelCost(estimatedNextRefuelCost)
                .build();
    }
}