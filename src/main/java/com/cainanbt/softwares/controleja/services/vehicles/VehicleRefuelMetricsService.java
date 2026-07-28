package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleRepository;
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
    private final VehicleRefuelCycleCalculator refuelCycleCalculator = new VehicleRefuelCycleCalculator();

    private final TransactionRepository transactionRepository;
    private final VehicleRepository vehicleRepository;

    /**
     * Recalcula toda a cadeia após criação, edição ou exclusão de um abastecimento.
     */
    public void recalculate(Vehicle vehicle) {
        List<Transactions> refuels = transactionRepository
                .findValidRefuelsByVehicleUpToDate(vehicle.getId(), Long.MAX_VALUE)
                .stream()
                .filter(transaction -> transaction.getDate() != null)
                .sorted(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null
                                ? transaction.getCreatedAt()
                                : 0L))
                .toList();

        refuels.forEach(refuel -> refuel.setEfficiency(null));

        double gasolineTotal = 0.0;
        int gasolineCount = 0;
        double ethanolTotal = 0.0;
        int ethanolCount = 0;

        for (VehicleRefuelCycleCalculator.VehicleRefuelCycle cycle : refuelCycleCalculator.buildReliableCycles(refuels, vehicle)) {
            Transactions closingFullTank = cycle.closingFullTank();
            closingFullTank.setEfficiency(cycle.kml());
            if (cycle.kml() > 0) {
                if (closingFullTank.getFuelType() == FuelType.GASOLINA) {
                    gasolineTotal += cycle.kml();
                    gasolineCount++;
                } else if (closingFullTank.getFuelType() == FuelType.ETANOL) {
                    ethanolTotal += cycle.kml();
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
