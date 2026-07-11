package com.cainanbt.softwares.controleja.dtos.invoices;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InvoicePaymentRequestDTO {
    @NotNull(message = "A conta de pagamento é obrigatória")
    private UUID accountId;

    @NotNull(message = "O valor do pagamento é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor do pagamento deve ser maior que zero")
    private BigDecimal amount;

    private Long paymentDate;
    private String notes;
    private Boolean advancePayment;
}
