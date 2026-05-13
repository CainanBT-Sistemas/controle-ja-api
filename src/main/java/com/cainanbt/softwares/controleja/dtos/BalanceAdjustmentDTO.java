package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BalanceAdjustmentDTO {
    @NotNull(message = "O novo saldo é obrigatório")
    private BigDecimal newBalance;
}
