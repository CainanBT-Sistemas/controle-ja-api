package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.CreditCard;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreditCardResponseDTO {
    private UUID id;
    private String name;
    private BigDecimal currentLimit;
    private BigDecimal totalLimit;
    private int closeDay;
    private int bestDay;
    private Boolean enabled;

    public static CreditCardResponseDTO toDTO(CreditCard entity) {
        CreditCardResponseDTO dto = new CreditCardResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTotalLimit(entity.getTotalLimit());
        dto.setCurrentLimit(entity.getCurrentLimit());
        dto.setCloseDay(entity.getCloseDay());
        dto.setBestDay(entity.getBestDay());
        dto.setEnabled(entity.getEnabled());
        return dto;
    }
}