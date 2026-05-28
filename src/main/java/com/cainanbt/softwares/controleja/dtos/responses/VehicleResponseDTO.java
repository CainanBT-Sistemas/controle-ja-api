package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Vehicle;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class VehicleResponseDTO {
    private UUID id;
    private String name;
    private String brand;
    private String model;
    private BigDecimal currentOdometer;
    private Double avgGasoline;
    private Double avgEthanol;
    private Integer year;
    private String plate;
    private Double tankCapacity; // NOVO CAMPO

    public static VehicleResponseDTO toDTO(Vehicle entity) {
        VehicleResponseDTO dto = new VehicleResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setBrand(entity.getBrand());
        dto.setModel(entity.getModel());
        dto.setCurrentOdometer(entity.getCurrentOdometer());
        dto.setAvgGasoline(entity.getAvgKmPerLiterGasoline());
        dto.setAvgEthanol(entity.getAvgKmPerLiterEthanol());
        dto.setPlate(entity.getPlate());
        dto.setYear(entity.getYear());
        dto.setTankCapacity(entity.getTankCapacity());
        return dto;
    }
}