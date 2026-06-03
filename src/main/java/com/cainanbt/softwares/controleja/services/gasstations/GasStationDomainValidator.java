package com.cainanbt.softwares.controleja.services.gasstations;

import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;

/**
 * Centraliza regras de propriedade e integridade dos postos de combustível.
 */
public class GasStationDomainValidator {

    /**
     * Garante que o posto pertence ao usuário autenticado antes de expor ou alterar dados.
     */
    public void validateOwner(GasStation station, Users currentUser) {
        if (station == null || station.getUser() == null || currentUser == null
                || !station.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Este posto não pertence a você.");
        }
    }

    /**
     * Bloqueia exclusão repetida de posto já removido.
     */
    public void validateCanDelete(GasStation station) {
        if (station.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }
    }
}
