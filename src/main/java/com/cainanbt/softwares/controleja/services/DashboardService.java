package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardFullSummaryDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;

import java.util.List;
import java.util.UUID;

public interface DashboardService {
    /**
     * Lista despesas gerais pagas agrupadas por categoria.
     */
    List<ChartDataDTO> getExpensesByCategory(Long start, Long end);

    /**
     * Lista despesas de cartão agrupadas por categoria.
     */
    List<ChartDataDTO> getCreditCardExpensesByCategory(Long start, Long end);

    /**
     * Lista receitas pagas agrupadas por categoria.
     */
    List<ChartDataDTO> getIncomesByCategory(Long start, Long end);

    /**
     * Lista gastos com combustível agrupados por tipo.
     */
    List<ChartDataDTO> getFuelComparison(Long start, Long end);

    /**
     * Lista evolução de despesas, com filtro opcional de categoria.
     */
    List<ChartDataDTO> getEvolution(Long start, Long end, UUID categoryId);

    /**
     * Calcula totais simples do período.
     */
    FinancialSummaryDTO getSummary(Long start, Long end);

    /**
     * Calcula visão consolidada do dashboard financeiro.
     */
    DashboardFullSummaryDTO getFullSummary(Long start, Long end);
}
