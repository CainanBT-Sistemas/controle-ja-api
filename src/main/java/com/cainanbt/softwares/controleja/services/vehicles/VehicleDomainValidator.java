package com.cainanbt.softwares.controleja.services.vehicles;

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

}
