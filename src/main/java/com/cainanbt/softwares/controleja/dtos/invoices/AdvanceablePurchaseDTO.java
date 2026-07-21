package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdvanceablePurchaseDTO {
    private UUID purchaseId;
    private String name;
    private int maxInstallmentsAvailable;
    private BigDecimal estimatedAmount;
    private List<BigDecimal> installmentAmounts;
}
