package com.cainanbt.softwares.controleja.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardFullSummaryDTO {
    private BigDecimal availableBalance;
    private BigDecimal projectedBalance;
    private BigDecimal projectedPayables;
    private BigDecimal projectedVariables;

    private List<DashboardAlertDTO> pendingPayables;
    private List<DashboardAlertDTO> pendingReceivables;
    private List<DashboardAlertDTO> pendingInvoices;
}
