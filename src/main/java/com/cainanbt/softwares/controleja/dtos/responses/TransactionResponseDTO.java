package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionResponseDTO {
    private UUID id;
    private String name;
    private TransactionType type;
    private BigDecimal amount;
    private Long date;
    private Boolean paid;
    private String categoryName;
    private String accountName;
    private Double efficiency;
    private String vehicleName;
    private Double liters;
    private BigDecimal currentOdometer;
    private FuelType fuelType;

    public static TransactionResponseDTO toDTO(Transactions entity) {
        TransactionResponseDTO dto = new TransactionResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setAmount(entity.getAmount());
        dto.setDate(entity.getDate());
        dto.setPaid(entity.getPaid());

        if (entity.getCategory() != null) {
            dto.setCategoryName(entity.getCategory().getName());
        }
        if (entity.getAccount() != null) {
            dto.setAccountName(entity.getAccount().getName());
        }
        if (entity.getVehicle() != null) {
            dto.setVehicleName(entity.getVehicle().getName());
            dto.setEfficiency(entity.getEfficiency());
            dto.setLiters(entity.getLiters());
            dto.setCurrentOdometer(entity.getCurrentOdometer());
            dto.setFuelType(entity.getFuelType());
        }

        return dto;
    }
}