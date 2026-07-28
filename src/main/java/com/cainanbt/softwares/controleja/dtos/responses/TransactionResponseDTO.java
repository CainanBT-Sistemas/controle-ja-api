package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class TransactionResponseDTO {
    private static final Pattern INSTALLMENT_SUFFIX = Pattern.compile(".*\\((\\d+)/(\\d+)\\)$");

    private UUID id;
    private String name;
    private TransactionType type;
    private BigDecimal amount;
    private Long date;
    private Boolean paid;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private UUID accountId;
    private String accountName;
    private UUID parentTransactionId;
    private Double efficiency;
    private String vehicleName;
    private UUID vehicleId;
    private Double liters;
    private Boolean fullTank;
    private BigDecimal currentOdometer;
    private FuelType fuelType;
    private DrivingPredominance drivingPredominance;
    private UUID creditCardId;
    private UUID targetInvoiceId;
    private UUID recurrenceRuleId;
    private Boolean virtual;


    // CORREÇÃO: Campos que faltavam para o app ler o status do Toggle
    private Boolean isFixed;
    private RecurrenceFrequency recurrenceFrequency;
    private Integer currentInstallment;
    private Integer totalInstallmentsPlan;

    public static TransactionResponseDTO toBasicDTO(Transactions entity) {
        TransactionResponseDTO dto = new TransactionResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setAmount(entity.getAmount());
        dto.setDate(entity.getDate());
        dto.setPaid(entity.getPaid());

        if (entity.getCategory() != null) {
            dto.setCategoryName(entity.getCategory().getName());
            dto.setCategoryId(entity.getCategory().getId());
        }

        if (entity.getAccount() != null) {
            dto.setAccountName(entity.getAccount().getName());
            dto.setAccountId(entity.getAccount().getId());
        }

        if (entity.getParentTransaction() != null) {
            dto.setParentTransactionId(entity.getParentTransaction().getId());
        }

        applyInstallmentMetadataFromName(dto, entity.getName());

        if (entity.getCreditCard() != null) {
            dto.setCreditCardId(entity.getCreditCard().getId());
        }

        if (entity.getTargetInvoice() != null) {
            dto.setTargetInvoiceId(entity.getTargetInvoice().getId());
        }

        return dto;
    }

    public static TransactionResponseDTO toDetailedDTO(Transactions entity) {
        TransactionResponseDTO dto = toBasicDTO(entity);

        dto.setDescription(entity.getDescription());
        dto.setIsFixed(entity.getFixed());

        if (entity.getRecurrenceRule() != null) {
            dto.setRecurrenceRuleId(entity.getRecurrenceRule().getId());
            dto.setRecurrenceFrequency(entity.getRecurrenceRule().getFrequency());
        }

        if (entity.getVehicle() != null) {
            dto.setVehicleName(entity.getVehicle().getName());
            dto.setEfficiency(entity.getEfficiency());
            dto.setLiters(entity.getLiters());
            dto.setFullTank(Boolean.TRUE.equals(entity.getFullTank()));
            dto.setCurrentOdometer(entity.getCurrentOdometer());
            dto.setFuelType(entity.getFuelType());
            dto.setDrivingPredominance(entity.getDrivingPredominance());
            dto.setVehicleId(entity.getVehicle().getId());
        }

        return dto;
    }

    private static void applyInstallmentMetadataFromName(TransactionResponseDTO dto, String name) {
        if (name == null) return;

        Matcher matcher = INSTALLMENT_SUFFIX.matcher(name.trim());
        if (!matcher.matches()) return;

        dto.setCurrentInstallment(Integer.parseInt(matcher.group(1)));
        dto.setTotalInstallmentsPlan(Integer.parseInt(matcher.group(2)));
    }
}
