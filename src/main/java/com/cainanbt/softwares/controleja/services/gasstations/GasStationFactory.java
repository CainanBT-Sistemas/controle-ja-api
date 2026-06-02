package com.cainanbt.softwares.controleja.services.gasstations;

import com.cainanbt.softwares.controleja.dtos.GasStationDTO;
import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.utils.ID;

/**
 * Monta entidades de posto mantendo construção separada da persistência.
 */
public class GasStationFactory {

    /**
     * Cria um posto a partir do contrato recebido pela API.
     */
    public GasStation create(GasStationDTO dto, Users user, long now) {
        return GasStation.builder()
                .id(ID.generate())
                .name(dto.getName())
                .address(dto.getAddress())
                .city(dto.getCity())
                .state(dto.getState())
                .user(user)
                .createdAt(now)
                .build();
    }
}
