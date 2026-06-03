package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDTO {

    @NotBlank(message = "O nome da conta é obrigatório (ex: Minha Carteira)")
    @Size(max = 80, message = "O nome da conta deve ter no máximo 80 caracteres")
    private String name;

    @NotNull(message = "O tipo é obrigatório (ex: WALLET, BANK, SAVINGS, INVESTMENT)")
    private AccountType type;

    @Size(max = 80, message = "A instituição deve ter no máximo 80 caracteres")
    private String institution;

    @NotNull(message = "O saldo inicial é obrigatório")
    private BigDecimal initialBalance;

    private String icon;
    private String color;
    private Boolean isDefault;
    private Boolean calculateBalance;
}
