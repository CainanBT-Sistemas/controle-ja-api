package com.cainanbt.softwares.controleja.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransactionDTO {

    @NotBlank(message = "A descrição/nome é obrigatória")
    private String name;
    private String description;

    @NotBlank(message = "O tipo é obrigatório (RECEITA, DESPESA)")
    private String type;

    @NotNull(message = "O valor é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "A data é obrigatória")
    private Long date;

    @NotNull(message = "A conta é obrigatória")
    private UUID accountId;

    @NotNull(message = "A categoria é obrigatória")
    private UUID categoryId;

    @NotNull
    private Boolean paid;
}
