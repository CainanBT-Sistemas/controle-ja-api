package com.cainanbt.softwares.controleja.dtos.dashboard;

import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CreditCardResponseDTO;
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
    private List<DashboardAlertDTO> overdueReceivables;

    private List<AccountResponseDTO> accounts;
    private List<CreditCardResponseDTO> creditCards;
    private List<DashboardAlertDTO> overduePayables;
    private List<DashboardAlertDTO> overdueInvoices;
}
