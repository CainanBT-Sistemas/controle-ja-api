package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.GasStation;
import lombok.Data;

import java.util.UUID;

@Data
public class GasStationResponseDTO {
    private UUID id;
    private String name;
    private String address;
    private String city;
    private String state;

    public static GasStationResponseDTO toDTO(GasStation entity) {
        GasStationResponseDTO dto = new GasStationResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setAddress(entity.getAddress());
        dto.setCity(entity.getCity());
        dto.setState(entity.getState());
        return dto;
    }
}
