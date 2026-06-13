package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.GasStationService;
import com.cainanbt.softwares.controleja.services.VehicleService;
import com.cainanbt.softwares.controleja.services.vehicles.VehicleConsumptionCalculator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Aplica os campos de veiculo em uma transacao financeira.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VehicleTransactionProcessor {
    private final VehicleService vehicleService;
    private final GasStationService gasStationService;
    private final TransactionRepository transactionRepository;
    private final VehicleConsumptionCalculator vehicleConsumptionCalculator;

    /**
     * Enriquece o builder com veiculo, posto e metricas sem misturar essa regra com processadores financeiros.
     */
    public void apply(TransactionDTO dto, Transactions.TransactionsBuilder builder, Users user) {
        applyGasStation(dto, builder, user);
        if (dto.getVehicleId() == null) {
            return;
        }

        Vehicle vehicle = vehicleService.findById(dto.getVehicleId());
        if (!vehicle.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }

        Double efficiency = processVehicleMetrics(dto, vehicle);
        builder.vehicle(vehicle)
                .liters(dto.getLiters())
                .currentOdometer(dto.getCurrentOdometer())
                .fuelType(dto.getFuelType())
                .drivingPredominance(dto.getDrivingPredominance())
                .efficiency(efficiency);
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

    /**
     * Calcula KM/L apenas quando existe abastecimento anterior confiavel.
     */
    private Double processVehicleMetrics(TransactionDTO dto, Vehicle vehicle) {
        if (dto.getCurrentOdometer() == null) {
            return null;
        }
        if (!isRefuel(dto)) {
            return null;
        }

        Transactions previousRefuel = transactionRepository
                .findPreviousValidRefuelsByVehicleBeforeDate(vehicle.getId(), dto.getDate())
                .stream()
                .findFirst()
                .orElse(null);
        if (previousRefuel == null || previousRefuel.getCurrentOdometer() == null) {
            log.info("Primeiro abastecimento do veículo {} registrado sem cálculo de KM/L.", vehicle.getId());
            return null;
        }
        return vehicleConsumptionCalculator.calculateConsumption(
                previousRefuel.getCurrentOdometer(),
                dto.getCurrentOdometer(),
                dto.getLiters()
        );
    }

    /**
     * Identifica um abastecimento por campos tecnicos, sem depender do nome da categoria.
     */
    private boolean isRefuel(TransactionDTO dto) {
        return dto.getLiters() != null && dto.getLiters() > 0 && dto.getFuelType() != null;
    }
}
