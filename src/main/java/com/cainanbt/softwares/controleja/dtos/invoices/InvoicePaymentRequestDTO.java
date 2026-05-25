package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InvoicePaymentRequestDTO {
    private UUID accountId;
    private BigDecimal amount;
    private Long paymentDate;
    private String notes;
}
