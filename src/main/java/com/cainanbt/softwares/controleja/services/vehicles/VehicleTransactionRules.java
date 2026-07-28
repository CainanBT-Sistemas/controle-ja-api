package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Transactions;

/**
 * Define o que é abastecimento para odômetro e consumo.
 */
public final class VehicleTransactionRules {
    private VehicleTransactionRules() {
    }

    public static boolean isRefuel(TransactionDTO dto) {
        return dto != null
                && dto.getLiters() != null
                && dto.getLiters() > 0
                && dto.getCurrentOdometer() != null
                && dto.getCurrentOdometer().signum() > 0;
    }

    public static boolean isRefuel(Transactions transaction) {
        return transaction != null
                && transaction.getLiters() != null
                && transaction.getLiters() > 0
                && transaction.getCurrentOdometer() != null
                && transaction.getCurrentOdometer().signum() > 0;
    }

    public static boolean isFullTank(Transactions transaction) {
        return Boolean.TRUE.equals(transaction != null ? transaction.getFullTank() : null);
    }
}
