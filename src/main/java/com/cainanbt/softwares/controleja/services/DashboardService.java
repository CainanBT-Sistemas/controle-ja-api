package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final TransactionRepository repository;

    public DashboardService(TransactionRepository repository) {
        this.repository = repository;
    }

    public List<ChartDataDTO> getExpensesByCategory(Long start, Long end) {
        return repository.getExpensesByCategory(getUser(), start, end, TransactionType.DESPESA);
    }

    public List<ChartDataDTO> getIncomesByCategory(Long start, Long end) {
        return repository.getExpensesByCategory(getUser(), start, end, TransactionType.RECEITA);
    }

    public List<ChartDataDTO> getFuelComparison(Long start, Long end) {
        return repository.getExpensesByFuelType(getUser(), start, end);
    }

    public List<ChartDataDTO> getEvolution(Long start, Long end, UUID categoryId) {
        // Retorna os dados ordenados por data. O front agrupa por dia/semana se precisar.
        return repository.getEvolutionRawData(getUser(), start, end, categoryId);
    }

    public FinancialSummaryDTO getSummary(Long start, Long end) {
        UUID userId = getUser();
        BigDecimal income = repository.getTotalByType(userId, TransactionType.RECEITA, start, end);
        BigDecimal expense = repository.getTotalByType(userId, TransactionType.DESPESA, start, end);

        if (income == null) income = BigDecimal.ZERO;
        if (expense == null) expense = BigDecimal.ZERO;

        return FinancialSummaryDTO.builder()
                .totalIncome(income)
                .totalExpense(expense)
                .balance(income.subtract(expense))
                .build();
    }

    private UUID getUser() {
        return SecurityContextUtils.getCurrentUser().getId();
    }
}