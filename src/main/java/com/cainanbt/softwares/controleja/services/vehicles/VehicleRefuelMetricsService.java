package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Reconstrói eficiências e médias do veículo a partir do histórico ativo de abastecimentos.
 */
@Service
@RequiredArgsConstructor
public class VehicleRefuelMetricsService {
    private final VehicleConsumptionCalculator consumptionCalculator = new VehicleConsumptionCalculator();

    private final TransactionRepository transactionRepository;
    private final VehicleRepository vehicleRepository;

    /**
     * Recalcula toda a cadeia após criação, edição ou exclusão de um abastecimento.
     */
    public void recalculate(Vehicle vehicle) {
        List<Transactions> refuels = transactionRepository
                .findValidRefuelsByVehicleUpToDate(vehicle.getId(), Long.MAX_VALUE)
                .stream()
                .sorted(Comparator.<Transactions, java.time.LocalDate>comparing(
                                transaction -> DateUtils.epochToLocalDate(transaction.getDate()))
                        .thenComparing(transaction -> transaction.getCreatedAt() != null
                                ? transaction.getCreatedAt()
                                : 0L))
                .toList();

        Transactions lastFullTank = null;
        double accumulatedLitersSinceLastFullTank = 0.0;
        double gasolineTotal = 0.0;
        int gasolineCount = 0;
        double ethanolTotal = 0.0;
        int ethanolCount = 0;

        for (Transactions refuel : refuels) {
            Double efficiency = null;
            if (VehicleTransactionRules.isFullTank(refuel)) {
                if (lastFullTank != null) {
                    double cycleLiters = accumulatedLitersSinceLastFullTank
                            + (refuel.getLiters() != null ? refuel.getLiters() : 0.0);
                    efficiency = consumptionCalculator.calculateConsumption(
                            lastFullTank.getCurrentOdometer(),
                            refuel.getCurrentOdometer(),
                            cycleLiters);
                }
                lastFullTank = refuel;
                accumulatedLitersSinceLastFullTank = 0.0;
            } else if (lastFullTank != null && refuel.getLiters() != null) {
                accumulatedLitersSinceLastFullTank += refuel.getLiters();
            }

            refuel.setEfficiency(efficiency);

            if (efficiency != null && efficiency > 0) {
                if (refuel.getFuelType() == FuelType.GASOLINA) {
                    gasolineTotal += efficiency;
                    gasolineCount++;
                } else if (refuel.getFuelType() == FuelType.ETANOL) {
                    ethanolTotal += efficiency;
                    ethanolCount++;
                }
            }
        }

        if (!refuels.isEmpty()) {
            transactionRepository.saveAll(refuels);
        }
        vehicle.setAvgKmPerLiterGasoline(gasolineCount > 0 ? gasolineTotal / gasolineCount : null);
        vehicle.setAvgKmPerLiterEthanol(ethanolCount > 0 ? ethanolTotal / ethanolCount : null);
        vehicleRepository.save(vehicle);
    }
}
