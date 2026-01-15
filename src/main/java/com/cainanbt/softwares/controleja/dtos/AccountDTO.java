package com.cainanbt.softwares.controleja.dtos;

import com.cainanbt.softwares.controleja.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDTO {

    @NotBlank(message = "O nome da conta é obrigatório (ex: Minha Carteira)")
    private String name;

    @NotNull(message = "O tipo é obrigatório (ex: WALLET, BANK, SAVINGS)")
    private AccountType type;

    private String institution;

    @NotNull(message = "O saldo inicial é obrigatório")
    private BigDecimal initialBalance;


}