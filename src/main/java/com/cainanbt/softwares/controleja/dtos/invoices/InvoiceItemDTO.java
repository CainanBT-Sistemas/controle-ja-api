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
    private String description;
    private Long date;
    private Long transactionDate;
    private String name;
    private UUID categoryId;
    private String categoryName;
    private UUID accountId;
    private String accountName;
    private UUID creditCardId;
    private Integer currentInstallment;
    private Integer totalInstallmentsPlan;
    private String type;
    private BigDecimal amount;
    private Boolean paid;
    private Boolean fixed;
    private Boolean canEdit;
    private String itemKind;
}
