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
    private Long expirationDate;
    private Long closeDate;
    private String status;
    private List<InvoiceItemDTO> items;
}
