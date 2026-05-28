package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class InvoiceItemDTO {
    private UUID id;
    private UUID transactionId;
    private UUID purchaseId;
    private Long date;
    private String name;
    private String categoryName;
    private Integer currentInstallment;
    private Integer totalInstallmentsPlan;
    private String type;
    private BigDecimal amount;
    private Boolean canEdit;
    private String itemKind;
}
