package com.cainanbt.softwares.controleja.dtos.invoices;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AdvanceRequestDTO {
    @NotNull(message = "A compra é obrigatória")
    private UUID purchaseId;

    @Min(value = 1, message = "A quantidade de parcelas para adiantar deve ser maior que zero")
    private Integer quantityToAdvance;

    @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo")
    private BigDecimal discountAmount;
}
