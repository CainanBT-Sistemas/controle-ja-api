package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.dashboard.ChartDataDTO;
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
import com.cainanbt.softwares.controleja.services.dashboard.DashboardAlertBuckets;
import com.cainanbt.softwares.controleja.services.dashboard.DashboardAlertMapper;
import com.cainanbt.softwares.controleja.services.dashboard.DashboardPeriodValidator;
import com.cainanbt.softwares.controleja.services.dashboard.DashboardProjectionCalculator;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDateService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class DashboardServiceImp implements DashboardService {
    private static final long THREE_MONTHS_IN_MILLIS = 90L * 24 * 60 * 60 * 1000;

    private final DashboardPeriodValidator periodValidator = new DashboardPeriodValidator();
    private final DashboardAlertMapper alertMapper = new DashboardAlertMapper();
    private final DashboardProjectionCalculator projectionCalculator = new DashboardProjectionCalculator();

    private final TransactionRepository repository;
    private final AccountsRepository accountsRepository;
    private final InvoicesRepository invoicesRepository;
    private final AccountsService accountsService;
    private final CreditCardService creditCardService;
    private final InvoiceDateService invoiceDateService;

    /**
     * Lista despesas pagas por categoria, exceto despesas de cartão de crédito.
     */
    @Override
    public List<ChartDataDTO> getExpensesByCategory(Long start, Long end) {
        periodValidator.validate(start, end);
        return repository.getGeneralExpensesByCategory(currentUserId(), start, end, TransactionType.DESPESA);
    }

    /**
     * Lista despesas de cartão de crédito por categoria usando parcelas do período.
     */
    @Override
    public List<ChartDataDTO> getCreditCardExpensesByCategory(Long start, Long end) {
        periodValidator.validate(start, end);
        return repository.getCreditCardExpensesByCategory(currentUserId(), start, end, TransactionType.DESPESA);
    }

    /**
     * Lista receitas pagas por categoria.
     */
    @Override
    public List<ChartDataDTO> getIncomesByCategory(Long start, Long end) {
        periodValidator.validate(start, end);
        return repository.getExpensesByCategory(currentUserId(), start, end, TransactionType.RECEITA);
    }

    /**
     * Agrupa gastos de abastecimento por tipo de combustível.
     */
    @Override
    public List<ChartDataDTO> getFuelComparison(Long start, Long end) {
        periodValidator.validate(start, end);
        return repository.getExpensesByFuelType(currentUserId(), start, end);
    }

    /**
     * Retorna evolução de despesas do período, filtrando por categoria quando informado.
     */
    @Override
    public List<ChartDataDTO> getEvolution(Long start, Long end, UUID categoryId) {
        periodValidator.validate(start, end);
        if (categoryId == null) {
            return repository.getEvolutionRawDataAll(currentUserId(), start, end);
        }
        return repository.getEvolutionRawDataByCategory(currentUserId(), start, end, categoryId);
    }

    /**
     * Calcula receita, despesa e saldo simples do período.
     */
    @Override
    public FinancialSummaryDTO getSummary(Long start, Long end) {
        periodValidator.validate(start, end);
        UUID userId = currentUserId();
        BigDecimal income = repository.getTotalByType(userId, TransactionType.RECEITA, start, end);
        BigDecimal expense = repository.getTotalByType(userId, TransactionType.DESPESA, start, end);
        income = projectionCalculator.nullToZero(income);
        expense = projectionCalculator.nullToZero(expense);

        return FinancialSummaryDTO.builder()
                .totalIncome(income)
                .totalExpense(expense)
                .balance(income.subtract(expense))
                .build();
    }

    /**
     * Monta o resumo completo com saldo, contas, cartões, alertas e projeções.
     */
    @Override
    public DashboardFullSummaryDTO getFullSummary(Long start, Long end) {
        periodValidator.validate(start, end);
        UUID userId = currentUserId();
        long nowEpoch = DateUtils.getEpochNow();
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        long todayEpoch = DateUtils.localDateToEpoch(today);

        BigDecimal availableBalance = projectionCalculator.nullToZero(accountsRepository.getAvailableBalanceByUserId(userId));

        List<AccountResponseDTO> accounts = accountsService.listMyAccountsExceptCrediCard().stream()
                .map(AccountResponseDTO::toDTO).toList();

        List<CreditCardResponseDTO> creditCards = creditCardService.listMyCards().stream()
                .map(CreditCardResponseDTO::toDTO).toList();

        DashboardAlertBuckets payableBuckets = splitTransactions(repository.findPendingUpToDate(userId, TransactionType.DESPESA, end), todayEpoch);
        DashboardAlertBuckets receivableBuckets = splitTransactions(repository.findPendingUpToDate(userId, TransactionType.RECEITA, end), todayEpoch);
        DashboardAlertBuckets invoiceBuckets = splitInvoices(invoicesRepository.findPendingInvoicesUpToDate(userId, end), today, todayEpoch);

        BigDecimal projectedPayables = payableBuckets.totalAmount().add(invoiceBuckets.totalAmount());
        BigDecimal projectedReceivables = receivableBuckets.totalAmount();
        long startThreeMonthsAgo = Math.max(0L, nowEpoch - THREE_MONTHS_IN_MILLIS);
        BigDecimal totalLast3Months = repository.getTotalByType(userId, TransactionType.DESPESA, startThreeMonthsAgo, nowEpoch);
        BigDecimal projectedVariables = projectionCalculator.averageLastThreeMonths(totalLast3Months);
        BigDecimal projectedBalance = projectionCalculator.projectedBalance(availableBalance, projectedReceivables, projectedPayables, projectedVariables);

        return DashboardFullSummaryDTO.builder()
                .availableBalance(availableBalance)
                .projectedBalance(projectedBalance)
                .projectedPayables(projectedPayables)
                .projectedVariables(projectedVariables)
                .pendingPayables(payableBuckets.getPending())
                .pendingReceivables(receivableBuckets.getPending())
                .pendingInvoices(invoiceBuckets.getPending())
                .overdueReceivables(receivableBuckets.getOverdue())
                .accounts(accounts)
                .creditCards(creditCards)
                .overduePayables(payableBuckets.getOverdue())
                .overdueInvoices(invoiceBuckets.getOverdue())
                .build();
    }

    /**
     * Separa transacoes pendentes entre vencidas e futuras.
     */
    private DashboardAlertBuckets splitTransactions(List<Transactions> transactions, long todayEpoch) {
        DashboardAlertBuckets buckets = new DashboardAlertBuckets();
        transactions.forEach(transaction -> buckets.add(alertMapper.fromTransaction(transaction), todayEpoch));
        return buckets;
    }

    /**
     * Separa faturas ja fechadas entre vencidas e a vencer.
     */
    private DashboardAlertBuckets splitInvoices(List<Invoices> invoices, LocalDate today, long todayEpoch) {
        DashboardAlertBuckets buckets = new DashboardAlertBuckets();
        invoices.stream()
                .filter(invoice -> shouldExposeInvoice(invoice, today))
                .forEach(invoice -> buckets.add(alertMapper.fromInvoice(invoice, canonicalInvoiceDueDate(invoice)), todayEpoch));
        return buckets;
    }

    private Long canonicalInvoiceDueDate(Invoices invoice) {
        if (invoice.getCreditCard() == null || invoice.getMonth() == null || invoice.getYear() == null) {
            return invoice.getExpirationDate();
        }
        LocalDate expirationDate = invoiceDateService.calculateExpirationDate(
                invoice.getCreditCard(),
                invoice.getMonth(),
                invoice.getYear()
        );
        return DateUtils.localDateToEpoch(expirationDate);
    }

    /**
     * Exibe apenas faturas de meses anteriores ou faturas cujo fechamento ja ocorreu.
     */
    private boolean shouldExposeInvoice(Invoices invoice, LocalDate today) {
        if (invoice.getCreditCard() == null) {
            return false;
        }
        boolean isPreviousMonth = invoice.getYear() < today.getYear()
                || (invoice.getYear() == today.getYear() && invoice.getMonth() < today.getMonthValue());
        LocalDate closeDate = invoiceDateService.calculateCloseDate(invoice.getCreditCard(), invoice.getMonth(), invoice.getYear());
        return isPreviousMonth || !today.isBefore(closeDate);
    }

    /**
     * Retorna o usuario autenticado para escopar todas as consultas do dashboard.
     */
    private UUID currentUserId() {
        return SecurityContextUtils.getCurrentUser().getId();
    }
}
