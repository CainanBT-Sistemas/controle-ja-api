package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDTO {

    @NotBlank(message = "O nome da conta é obrigatório (ex: Minha Carteira)")
    private String name;

    @NotBlank(message = "O tipo é obrigatório (ex: WALLET, BANK, SAVINGS)")
    private String type;

    private String institution;

    @NotNull(message = "O saldo inicial é obrigatório")
    private BigDecimal initialBalance;


}