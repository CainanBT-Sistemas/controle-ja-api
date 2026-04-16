package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class InvoiceItemDTO {
    private UUID id;
    private Long date;
    private String name;
    private Integer currentInstallment;
    private Integer totalInstallmentsPlan;
    private BigDecimal amount;
}
