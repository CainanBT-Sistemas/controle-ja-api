package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardAlertDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardFullSummaryDTO;
import com.cainanbt.softwares.controleja.dtos.dashboard.FinancialSummaryDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CreditCardResponseDTO;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.InvoicesRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.DashboardService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class DashboardServiceImp implements DashboardService {
    private final TransactionRepository repository;
    private final AccountsRepository accountsRepository;
    private final InvoicesRepository invoicesRepository;

    // Injeção dos novos serviços
    private final AccountsService accountsService;
    private final CreditCardService creditCardService;

    @Override
    public List<ChartDataDTO> getExpensesByCategory(Long start, Long end) {
        return repository.getGeneralExpensesByCategory(getUser(), start, end, TransactionType.DESPESA);
    }

    @Override
    public List<ChartDataDTO> getCreditCardExpensesByCategory(Long start, Long end) {
        return repository.getCreditCardExpensesByCategory(getUser(), start, end, TransactionType.DESPESA);
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
        long nowEpoch = DateUtils.getEpochNow();
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        long todayEpoch = DateUtils.localDateToEpoch(today); // Início do dia de hoje

        // 1. SNAPSHOT: Saldos, Contas e Cartões
        BigDecimal availableBalance = accountsRepository.getAvailableBalanceByUserId(userId);
        if (availableBalance == null) availableBalance = BigDecimal.ZERO;

        List<AccountResponseDTO> accounts = accountsService.listMyAccountsExceptCrediCard().stream()
                .map(AccountResponseDTO::toDTO).toList();

        List<CreditCardResponseDTO> creditCards = creditCardService.listMyCards().stream()
                .map(CreditCardResponseDTO::toDTO).toList();

        // 2. BUSCA NO BANCO
        List<Transactions> allPayables = repository.findPendingUpToDate(userId, TransactionType.DESPESA, end);
        List<Transactions> receivables = repository.findPendingUpToDate(userId, TransactionType.RECEITA, end);
        List<Invoices> rawInvoices = invoicesRepository.findPendingInvoicesUpToDate(userId, end);

        // 3. SEPARAÇÃO: Despesas Atrasadas vs Pendentes no Mês
        List<DashboardAlertDTO> overduePayables = new ArrayList<>();
        List<DashboardAlertDTO> pendingPayables = new ArrayList<>();

        for (Transactions t : allPayables) {
            DashboardAlertDTO alert = mapToAlertDTO(t);
            if (t.getDate() < todayEpoch) {
                overduePayables.add(alert); // Venceu antes de hoje = ATRASADO
            } else {
                pendingPayables.add(alert); // Vence hoje em diante = PENDENTE
            }
        }

        List<DashboardAlertDTO> overdueReceivables = new ArrayList<>();
        List<DashboardAlertDTO> pendingReceivables = new ArrayList<>();

        for (Transactions t : receivables) {
            DashboardAlertDTO alert = mapToAlertDTO(t);
            if (t.getDate() < todayEpoch) {
                overdueReceivables.add(alert);
            } else {
                pendingReceivables.add(alert);
            }
        }

        // 4. SEPARAÇÃO: Faturas Atrasadas vs Abertas
        List<DashboardAlertDTO> overdueInvoices = new ArrayList<>();
        List<DashboardAlertDTO> pendingInvoices = new ArrayList<>();

        for (Invoices inv : rawInvoices) {
            LocalDate closeDate = calculateCloseDate(inv);

            // Verifica se a fatura já fechou no mundo real
            boolean isPreviousMonth = (inv.getYear() < today.getYear()) || (inv.getYear() == today.getYear() && inv.getMonth() < today.getMonthValue());

            if (isPreviousMonth || !today.isBefore(closeDate)) {
                DashboardAlertDTO alert = DashboardAlertDTO.builder()
                        .id(inv.getId())
                        .referenceId(inv.getCreditCard() != null ? inv.getCreditCard().getId() : null)
                        .description("Fatura " + (inv.getCreditCard() != null ? inv.getCreditCard().getName() : ""))
                        .amount(inv.getAmount())
                        .dueDate(inv.getExpirationDate())
                        .icon(inv.getCreditCard() != null ? inv.getCreditCard().getIcon() : null)
                        .color(inv.getCreditCard() != null ? inv.getCreditCard().getColor() : null)
                        .type("FATURA")
                        .month(inv.getMonth())
                        .year(inv.getYear())
                        .build();

                if (inv.getExpirationDate() < todayEpoch) {
                    overdueInvoices.add(alert); // Venceu = ATRASADA
                } else {
                    pendingInvoices.add(alert); // Aberta/Fechada a Pagar = PENDENTE
                }
            }
        }

        // 5. CÁLCULO DE PROJEÇÕES
        BigDecimal projectedPayables = overduePayables.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(pendingPayables.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add))
                .add(overdueInvoices.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add))
                .add(pendingInvoices.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal projectedReceivables = overdueReceivables.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(pendingReceivables.stream().map(DashboardAlertDTO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));

        long startThreeMonthsAgo = Math.max(0L, nowEpoch - (90L * 24 * 60 * 60 * 1000));
        BigDecimal totalLast3Months = repository.getTotalByType(userId, TransactionType.DESPESA, startThreeMonthsAgo, nowEpoch);
        if (totalLast3Months == null) totalLast3Months = BigDecimal.ZERO;
        BigDecimal projectedVariables = totalLast3Months.divide(new BigDecimal(3), 2, RoundingMode.HALF_UP);

        BigDecimal projectedBalance = availableBalance.add(projectedReceivables).subtract(projectedPayables).subtract(projectedVariables);

        return DashboardFullSummaryDTO.builder()
                .availableBalance(availableBalance)
                .projectedBalance(projectedBalance)
                .projectedPayables(projectedPayables)
                .projectedVariables(projectedVariables)
                .pendingPayables(pendingPayables)
                .pendingReceivables(pendingReceivables)
                .pendingInvoices(pendingInvoices)
                .overdueReceivables(overdueReceivables)
                .accounts(accounts)                 // Listas formatadas
                .creditCards(creditCards)
                .overduePayables(overduePayables)
                .overdueInvoices(overdueInvoices)
                .build();
    }

    private DashboardAlertDTO mapToAlertDTO(Transactions t) {
        return DashboardAlertDTO.builder()
                .id(t.getId())
                .description(t.getName())
                .amount(t.getAmount())
                .dueDate(t.getDate())
                .icon(t.getCategory() != null ? t.getCategory().getIcon() : null)
                .color(t.getCategory() != null ? t.getCategory().getColor() : null)
                .type(t.getType().name())
                .build();
    }

    private UUID getUser() {
        return SecurityContextUtils.getCurrentUser().getId();
    }

    private LocalDate calculateCloseDate(Invoices invoice) {
        int monthLength = LocalDate.of(invoice.getYear(), invoice.getMonth(), 1).lengthOfMonth();
        LocalDate closeDate = LocalDate.of(invoice.getYear(), invoice.getMonth(), Math.min(invoice.getCreditCard().getCloseDay(), monthLength));
        if (invoice.getCreditCard().getCloseDay() > invoice.getCreditCard().getBestDay()) {
            closeDate = closeDate.minusMonths(1);
        }
        while (closeDate.getDayOfWeek() == DayOfWeek.SATURDAY || closeDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            closeDate = closeDate.plusDays(1);
        }
        return closeDate;
    }
}
