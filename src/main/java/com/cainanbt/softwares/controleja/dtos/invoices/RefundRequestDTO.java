package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RefundRequestDTO {
    private UUID installmentId;
    private BigDecimal refundAmount;
}
