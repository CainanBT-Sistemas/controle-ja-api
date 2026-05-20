package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
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
    private UUID categoryId;
    private String categoryName;
    private UUID accountId;
    private String accountName;
    private Double efficiency;
    private String vehicleName;
    private UUID vehicleId;
    private Double liters;
    private BigDecimal currentOdometer;
    private FuelType fuelType;
    private UUID recurrenceRuleId;

    // CORREÇÃO: Campos que faltavam para o app ler o status do Toggle
    private Boolean isFixed;
    private RecurrenceFrequency recurrenceFrequency;

    public static TransactionResponseDTO toDTO(Transactions entity) {
        TransactionResponseDTO dto = new TransactionResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setAmount(entity.getAmount());
        dto.setDate(entity.getDate());
        dto.setPaid(entity.getPaid());

        dto.setIsFixed(entity.getFixed());

        if (entity.getCategory() != null) {
            dto.setCategoryName(entity.getCategory().getName());
            dto.setCategoryId(entity.getCategory().getId());
        }
        if (entity.getAccount() != null) {
            dto.setAccountName(entity.getAccount().getName());
            dto.setAccountId(entity.getAccount().getId());
        }
        if (entity.getVehicle() != null) {
            dto.setVehicleName(entity.getVehicle().getName());
            dto.setEfficiency(entity.getEfficiency());
            dto.setLiters(entity.getLiters());
            dto.setCurrentOdometer(entity.getCurrentOdometer());
            dto.setFuelType(entity.getFuelType());
            dto.setVehicleId(entity.getVehicle().getId());
        }
        if (entity.getRecurrenceRule() != null) {
            dto.setRecurrenceRuleId(entity.getRecurrenceRule().getId());
            dto.setRecurrenceFrequency(entity.getRecurrenceRule().getFrequency());
        }

        return dto;
    }
}