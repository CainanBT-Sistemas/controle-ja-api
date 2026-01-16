package com.cainanbt.softwares.controleja.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinancialSummaryDTO {
    private BigDecimal totalIncome;    // Receita
    private BigDecimal totalExpense;   // Despesa
    private BigDecimal balance;        // Saldo (Economia)
}