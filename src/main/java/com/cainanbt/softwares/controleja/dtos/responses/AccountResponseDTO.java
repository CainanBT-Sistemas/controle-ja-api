package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.enums.AccountType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AccountResponseDTO {
    private UUID id;
    private String name;
    private AccountType type;
    private String institution;
    private BigDecimal currentBalance;
    private Boolean enabled;

    private String icon;
    private String color;
    private Boolean isDefault;

    public static AccountResponseDTO toDTO(Accounts entity) {
        AccountResponseDTO dto = new AccountResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setInstitution(entity.getInstitution());
        dto.setCurrentBalance(entity.getCurrentBalance());
        dto.setEnabled(entity.getEnabled());
        dto.setIcon(entity.getIcon());
        dto.setColor(entity.getColor());
        dto.setIsDefault(entity.getIsDefault());
        return dto;
    }
}
