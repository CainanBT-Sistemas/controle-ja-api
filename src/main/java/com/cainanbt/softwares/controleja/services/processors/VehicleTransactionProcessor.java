package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleTransactionRules;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aplica os campos de veiculo em uma transacao financeira.
 */
@Component
@RequiredArgsConstructor
public class VehicleTransactionProcessor {
    private final VehicleService vehicleService;
    private final GasStationService gasStationService;

    /**
     * Enriquece o builder com veiculo, posto e metricas sem misturar essa regra com processadores financeiros.
     */
    public void apply(TransactionDTO dto, Transactions.TransactionsBuilder builder, Users user) {
        if (dto.getType() != com.cainanbt.softwares.controleja.enums.TransactionType.DESPESA) {
            return;
        }
        if (dto.getVehicleId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(dto.getFullTank()) && !VehicleTransactionRules.isRefuel(dto)) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Tanque cheio só pode ser informado em abastecimentos.");
        }

        Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        builder.vehicle(vehicle)
                .drivingPredominance(dto.getDrivingPredominance())
                .fullTank(false);

        if (!VehicleTransactionRules.isRefuel(dto)) {
            return;
        }

        applyGasStation(dto, builder, user);
        builder.liters(dto.getLiters())
                .currentOdometer(dto.getCurrentOdometer())
                .fuelType(dto.getFuelType())
                .fullTank(Boolean.TRUE.equals(dto.getFullTank()))
                .efficiency(null);
    }

    /**
     * Registra o posto quando informado pelo lancamento.
     */
    private void applyGasStation(TransactionDTO dto, Transactions.TransactionsBuilder builder, Users user) {
        if (dto.getGasStationId() == null) {
            return;
        }
        GasStation station = gasStationService.findByIdOrThrow(dto.getGasStationId());
        if (station.getUser() == null || !station.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Este posto não pertence a você.");
        }
        builder.gasStation(station);
    }

}
