package com.cainanbt.softwares.controleja.services.vehicles;

import com.cainanbt.softwares.controleja.dtos.VehicleLogDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.entities.Vehicle;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.OdometerValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centraliza regras de integridade de veículo, posse e odômetro.
 */
@Component
public class VehicleDomainValidator {

    /**
     * Garante que o veículo pertence ao usuário autenticado.
     */
    public void validateOwner(Vehicle vehicle, Users currentUser) {
        if (vehicle.getUser() == null || !vehicle.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_VEHICLE);
        }
    }

    /**
     * Valida o odômetro inicial informado no cadastro do veículo.
     */
    public void validateInitialOdometer(BigDecimal odometer) {
        OdometerValidator.validateValue(odometer);
    }

    /**
     * Valida uma leitura nova de odômetro para impedir retrocesso e saltos irreais.
     */
    public void validateNextOdometer(Vehicle vehicle, BigDecimal newOdometer) {
        OdometerValidator.validateValue(newOdometer);
        BigDecimal current = vehicle.getCurrentOdometer() != null ? vehicle.getCurrentOdometer() : vehicle.getInitialOdometer();
        if (current == null) {
            return;
        }
        if (newOdometer.compareTo(current) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O odômetro informado deve ser maior que o odômetro atual do veículo.");
        }
        OdometerValidator.validateJump(current, newOdometer);
    }

    /**
     * Valida os campos de diário de bordo antes de criar o log.
     */
    public void validateLogRequest(Vehicle vehicle, VehicleLogDTO dto) {
        if (dto.getOdometerReading() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Odômetro é obrigatório.");
        }
        validateNextOdometer(vehicle, dto.getOdometerReading());
    }
}
