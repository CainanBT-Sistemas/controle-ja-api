package com.cainanbt.softwares.controleja.dtos.invoices;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RefundRequestDTO {
    @NotNull(message = "A parcela é obrigatória")
    private UUID installmentId;

    @NotNull(message = "O valor do estorno é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor do estorno deve ser maior que zero")
    private BigDecimal refundAmount;
}
