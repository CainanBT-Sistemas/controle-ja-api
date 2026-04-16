package com.cainanbt.softwares.controleja.dtos.invoices;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AdvanceablePurchaseDTO {
    private UUID purchaseId;
    private String name;
    private int maxInstallmentsAvailable;
}
