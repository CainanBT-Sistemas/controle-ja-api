package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Vehicle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reconstrói ciclos confiáveis de consumo entre tanques cheios.
 */
public class VehicleRefuelCycleCalculator {
    private static final double MAX_REFUEL_LITERS_WITHOUT_TANK_CAPACITY = 200.0;
    private static final double TANK_CAPACITY_TOLERANCE_FACTOR = 1.5;
    private static final double MAX_PLAUSIBLE_KML = 100.0;

    public List<VehicleRefuelCycle> buildReliableCycles(List<Transactions> refuels, Vehicle vehicle) {
        List<Transactions> orderedRefuels = refuels.stream()
                .filter(refuel -> isValidRefuel(refuel, vehicle))
                .sorted(Comparator.comparing(Transactions::getDate)
                        .thenComparing(transaction -> transaction.getCreatedAt() != null ? transaction.getCreatedAt() : 0L))
                .toList();

        CycleState state = new CycleState();
        for (Transactions refuel : orderedRefuels) {
            if (VehicleTransactionRules.isFullTank(refuel)) {
                state.closeCycleIfPossible(refuel);
                state.lastFullTank = refuel;
                state.accumulatedPartialLiters = 0.0;
                continue;
            }
            if (state.lastFullTank != null) {
                state.accumulatedPartialLiters += refuel.getLiters();
            }
        }
        return state.cycles;
    }

    private boolean isValidRefuel(Transactions refuel, Vehicle vehicle) {
        return VehicleTransactionRules.isRefuel(refuel)
                && refuel.getDate() != null
                && isPlausibleLiters(refuel.getLiters(), vehicle);
    }

    private boolean isPlausibleLiters(Double liters, Vehicle vehicle) {
        if (liters == null || liters <= 0) {
            return false;
        }
        if (vehicle != null && vehicle.getTankCapacity() != null && vehicle.getTankCapacity() > 0) {
            return liters <= vehicle.getTankCapacity() * TANK_CAPACITY_TOLERANCE_FACTOR;
        }
        return liters <= MAX_REFUEL_LITERS_WITHOUT_TANK_CAPACITY;
    }

    private static final class CycleState {
        private final ArrayList<VehicleRefuelCycle> cycles = new ArrayList<>();
        private Transactions lastFullTank;
        private double accumulatedPartialLiters;

        private void closeCycleIfPossible(Transactions closingFullTank) {
            if (lastFullTank == null) {
                return;
            }
            BigDecimal distanceKm = closingFullTank.getCurrentOdometer().subtract(lastFullTank.getCurrentOdometer());
            double liters = accumulatedPartialLiters + closingFullTank.getLiters();
            if (distanceKm.compareTo(BigDecimal.ZERO) <= 0 || liters <= 0) {
                return;
            }
            double kml = BigDecimal.valueOf(distanceKm.doubleValue() / liters)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            if (kml <= 0 || kml > MAX_PLAUSIBLE_KML) {
                return;
            }
            cycles.add(new VehicleRefuelCycle(lastFullTank, closingFullTank, distanceKm, liters, kml));
        }
    }

    public record VehicleRefuelCycle(
            Transactions startFullTank,
            Transactions closingFullTank,
            BigDecimal distanceKm,
            double liters,
            double kml) {
    }
}
