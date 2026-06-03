package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditCardDTO {
    @NotBlank(message = "O nome do cartão é obrigatório (ex: Nubank)")
    @Size(max = 80, message = "O nome do cartão deve ter no máximo 80 caracteres")
    private String name;

    @NotNull(message = "O limite é obrigatório")
    @DecimalMin(value = "0.0", inclusive = false, message = "O limite deve ser maior que zero")
    private BigDecimal totalLimit;

    @Min(value = 1, message = "Dia de fechamento inválido")
    @Max(value = 31, message = "Dia de fechamento inválido")
    private int closeDay;

    @Min(value = 1, message = "Dia de vencimento inválido")
    @Max(value = 31, message = "Dia de vencimento inválido")
    private int bestDay;

    private String icon;
    private String color;
}
