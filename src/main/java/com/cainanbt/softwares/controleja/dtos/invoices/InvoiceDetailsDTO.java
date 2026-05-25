package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InvoiceDetailsDTO {
    private UUID invoiceId;
    private UUID cardId;
    private String cardName;
    private Integer month;
    private Integer year;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal openAmount;
    private Long expirationDate;
    private Long closeDate;
    private String status;
    private Boolean canPay;
    private Boolean canAdvancePayment;
    private Boolean canAdvanceInstallments;
    private Boolean canRefund;
    private Boolean canEditTransactions;
    private Boolean canEditCard;
    private List<InvoiceItemDTO> items;
}
