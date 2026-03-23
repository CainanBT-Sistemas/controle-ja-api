package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;

import java.util.List;
import java.util.UUID;

public interface DashboardService {
    List<ChartDataDTO> getExpensesByCategory(Long start, Long end);

    List<ChartDataDTO> getIncomesByCategory(Long start, Long end);

    List<ChartDataDTO> getFuelComparison(Long start, Long end);

    List<ChartDataDTO> getEvolution(Long start, Long end, UUID categoryId);

    FinancialSummaryDTO getSummary(Long start, Long end);
}