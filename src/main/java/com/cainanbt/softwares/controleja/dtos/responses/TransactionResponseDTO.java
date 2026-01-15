package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Transactions;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionResponseDTO {
    private UUID id;
    private String name;
    private String type;
    private BigDecimal amount;
    private Long date;
    private Boolean paid;
    private String categoryName; // Facilitador
    private String accountName;  // Facilitador

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

        return dto;
    }
}