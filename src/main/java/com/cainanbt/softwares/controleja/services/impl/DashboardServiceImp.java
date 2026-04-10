package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardAlertDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardFullSummaryDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.InvoicesRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.DashboardService;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class DashboardServiceImp implements DashboardService {

    private final TransactionRepository repository;
    private final AccountsRepository accountsRepository;
    private final InvoicesRepository invoicesRepository;

    @Override
    public List<ChartDataDTO> getExpensesByCategory(Long start, Long end) {
        return repository.getExpensesByCategory(getUser(), start, end, TransactionType.DESPESA);
    }

    @Override
    public List<ChartDataDTO> getIncomesByCategory(Long start, Long end) {
        return repository.getExpensesByCategory(getUser(), start, end, TransactionType.RECEITA);
    }

    @Override
    public List<ChartDataDTO> getFuelComparison(Long start, Long end) {
        return repository.getExpensesByFuelType(getUser(), start, end);
    }

    @Override
    public List<ChartDataDTO> getEvolution(Long start, Long end, UUID categoryId) {
        if (categoryId == null) {
            return repository.getEvolutionRawDataAll(getUser(), start, end);
        } else {
            return repository.getEvolutionRawDataByCategory(getUser(), start, end, categoryId);
        }
    }

    @Override
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

    @Override
    public DashboardFullSummaryDTO getFullSummary(Long start, Long end) {
        UUID userId = getUser();

        // available balance
        BigDecimal availableBalance = accountsRepository.getAvailableBalanceByUserId(userId);
        if (availableBalance == null) availableBalance = BigDecimal.ZERO;

        // Busca transações pendentes normais
        List<com.cainanbt.softwares.controleja.entities.Transactions> payables = repository.findPendingUpToDate(userId, TransactionType.DESPESA, end);
        List<com.cainanbt.softwares.controleja.entities.Transactions> receivables = repository.findPendingUpToDate(userId, TransactionType.RECEITA, end);

        // Busca todas as faturas até a data final do mês selecionado
        List<com.cainanbt.softwares.controleja.entities.Invoices> rawInvoices = invoicesRepository.findPendingInvoicesUpToDate(userId, end);

        // Pega a data EXATA de hoje, ignorando o mês que o usuário selecionou no app
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

        // FILTRO INTELIGENTE E DEFINITIVO DE FATURAS
        List<com.cainanbt.softwares.controleja.entities.Invoices> invoices = rawInvoices.stream().filter(inv -> {

            java.time.LocalDate closeDate;
            try {
                closeDate = java.time.LocalDate.of(inv.getYear(), inv.getMonth(), inv.getCreditCard().getCloseDay());
            } catch (java.time.DateTimeException e) {
                closeDate = java.time.LocalDate.of(inv.getYear(), inv.getMonth(), 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
            }
            return !today.isBefore(closeDate);

        }).toList();


        // map transactions to DashboardAlertDTO
        List<DashboardAlertDTO> pendingPayables = payables.stream().map(t -> DashboardAlertDTO.builder()
                .id(t.getId())
                .description(t.getName())
                .amount(t.getAmount())
                .dueDate(t.getDate())
                .icon(t.getCategory() != null ? t.getCategory().getIcon() : null)
                .color(t.getCategory() != null ? t.getCategory().getColor() : null)
                .type("DESPESA")
                .build()).collect(Collectors.toList());

        List<DashboardAlertDTO> pendingReceivables = receivables.stream().map(t -> DashboardAlertDTO.builder()
                .id(t.getId())
                .description(t.getName())
                .amount(t.getAmount())
                .dueDate(t.getDate())
                .icon(t.getCategory() != null ? t.getCategory().getIcon() : null)
                .color(t.getCategory() != null ? t.getCategory().getColor() : null)
                .type("RECEITA")
                .build()).collect(Collectors.toList());

        List<DashboardAlertDTO> pendingInvoices = invoices.stream().map(i -> DashboardAlertDTO.builder()
                .id(i.getId())
                .description("Fatura " + (i.getCreditCard() != null ? i.getCreditCard().getName() : ""))
                .amount(i.getAmount())
                .dueDate(i.getExpirationDate())
                .icon(i.getCreditCard() != null ? i.getCreditCard().getIcon() : null)
                .color(i.getCreditCard() != null ? i.getCreditCard().getColor() : null)
                .type("FATURA")
                .build()).collect(Collectors.toList());

        // projected payables sum
        BigDecimal projectedPayables = pendingPayables.stream()
                .map(DashboardAlertDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // projected receivables sum (from pending receivables)
        BigDecimal projectedReceivables = pendingReceivables.stream()
                .map(DashboardAlertDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // projected variables: average expenses over the last 3 months
        long endNow = (end != null) ? end : System.currentTimeMillis();
        long threeMonthsMillis = 90L * 24 * 60 * 60 * 1000; // approx 90 days
        long startThreeMonthsAgo = Math.max(0L, endNow - threeMonthsMillis);

        BigDecimal totalLast3Months = repository.getTotalByType(userId, TransactionType.DESPESA, startThreeMonthsAgo, endNow);
        if (totalLast3Months == null) totalLast3Months = BigDecimal.ZERO;
        BigDecimal projectedVariables = totalLast3Months.divide(new BigDecimal(3), 2, RoundingMode.HALF_UP);

        // projected balance: available + projectedReceivables - projectedPayables - projectedVariables
        BigDecimal projectedBalance = availableBalance.add(projectedReceivables).subtract(projectedPayables).subtract(projectedVariables);

        return DashboardFullSummaryDTO.builder()
                .availableBalance(availableBalance)
                .projectedBalance(projectedBalance)
                .projectedPayables(projectedPayables)
                .projectedVariables(projectedVariables)
                .pendingPayables(pendingPayables)
                .pendingReceivables(pendingReceivables)
                .pendingInvoices(pendingInvoices)
                .build();
    }

    private UUID getUser() {
        return SecurityContextUtils.getCurrentUser().getId();
    }
}