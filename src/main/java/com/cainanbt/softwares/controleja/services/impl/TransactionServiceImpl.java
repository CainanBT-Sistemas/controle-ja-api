package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CategoryService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.services.processors.TransactionHelper;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessor;
import com.cainanbt.softwares.controleja.services.processors.TransactionProcessorFactory;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final AccountsService accountsService;
    private final CategoryService categoryService;
    private final CreditCardService creditCardService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final RecurrenceRuleService recurrenceRuleService;

    private final TransactionProcessorFactory processorFactory;
    private final TransactionHelper helper;

    @Override
    @Transactional
    public Transactions createTransaction(TransactionDTO dto) {
        Users user = SecurityContextUtils.getCurrentUser();

        Accounts account = accountsService.findById(dto.getAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }

        Category category = categoryService.findById(dto.getCategoryId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));

        TransactionProcessor processor = processorFactory.getProcessor(dto, account);
        return processor.process(dto, account, category, user);
    }

    @Override
    public List<Transactions> listLastTransactions(Long start, Long end) {
        Users user = SecurityContextUtils.getCurrentUser();
        if (start != null && end != null) {
            return repository.findTransactionsByMonth(user.getId(), start, end);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<Transactions> findById(UUID id) {
        return repository.findByIdAndNotDeleted(id);
    }

    @Override
    public Transactions findByIdOrThrow(UUID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(ConstsMessages.ERROR_TITLE, ConstsMessages.TRANSACTION_NOT_FOUND));
    }

    @Override
    @Transactional
    public Transactions updateTransaction(UUID id, TransactionDTO dto, Boolean updateFuture) {
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (dto.getName() != null) transaction.setName(dto.getName());
        if (dto.getDescription() != null) transaction.setDescription(dto.getDescription());
        if (dto.getType() != null) transaction.setType(dto.getType());

        if (dto.getAmount() != null) {
            transaction.setAmount(dto.getAmount());
            if (Boolean.TRUE.equals(updateFuture) && transaction.getRecurrenceRule() != null) {
                cascadeRuleUpdate(transaction.getRecurrenceRule().getId(), dto.getAmount());
            }
        }

        if (dto.getDate() != null) transaction.setDate(dto.getDate());
        if (dto.getPaid() != null) transaction.setPaid(dto.getPaid());

        if (dto.getAccountId() != null) {
            Accounts account = accountsService.findById(dto.getAccountId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ACCOUNT_NOT_FOUND));
            if (!account.getUser().getId().equals(currentUser.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
            }
            transaction.setAccount(account);
        }
        if (dto.getCategoryId() != null) {
            Category category = categoryService.findById(dto.getCategoryId())
                    .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CATEGORY_NOT_FOUND));
            transaction.setCategory(category);
        }

        transaction.setUpdatedAt(dateNow);
        return repository.save(transaction);
    }

    @Override
    @Transactional
    public void softDelete(UUID id, Boolean cancelFuture) {
        Transactions transaction = findByIdOrThrow(id);
        Users currentUser = SecurityContextUtils.getCurrentUser();
        long dateNow = DateUtils.getEpochNow();

        if (!transaction.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_TRANSACTION);
        }

        if (transaction.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.ENTITY_ALREADY_DELETED);
        }

        if (Boolean.TRUE.equals(cancelFuture)) {
            if (transaction.getRecurrenceRule() != null) {
                RecurrenceRule rule = transaction.getRecurrenceRule();
                rule.setStatus(RuleStatus.CANCELED);
                rule.setUpdatedAt(dateNow);
                recurrenceRuleService.save(rule);

                List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(rule.getId(), dateNow);
                for (Transactions tx : futureUnpaidTx) {
                    tx.setDeletedAt(dateNow);
                    if (tx.getAccount().getType() == AccountType.CREDIT_CARD) {
                        deleteInstallmentsAndRestoreInvoice(tx.getId(), dateNow);
                    }
                }
                repository.saveAll(futureUnpaidTx);
            } else {
                repository.deleteByParentId(transaction.getId(), dateNow);
            }
        }

        if (transaction.getAccount().getType() == AccountType.CREDIT_CARD) {
            deleteInstallmentsAndRestoreInvoice(transaction.getId(), dateNow);

            CreditCard card = creditCardService.findByAccountId(transaction.getAccount().getId());
            card.restoreLimit(transaction.getAmount());
            creditCardService.updateLimit(card);
        }

        transaction.setDeletedAt(dateNow);
        repository.save(transaction);
    }

    @Override
    @Transactional
    public void cascadeRuleUpdate(UUID ruleId, BigDecimal newAmount) {
        RecurrenceRule rule = recurrenceRuleService.findByIdOrThrow(ruleId);
        BigDecimal oldAmount = rule.getBaseAmount();
        BigDecimal difference = newAmount.subtract(oldAmount);

        rule.setBaseAmount(newAmount);
        rule.setUpdatedAt(DateUtils.getEpochNow());
        recurrenceRuleService.save(rule);

        List<Transactions> futureUnpaidTx = repository.findFutureUnpaidByRuleId(ruleId, DateUtils.getEpochNow());
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();

        for (Transactions tx : futureUnpaidTx) {
            tx.setAmount(newAmount);
            tx.setUpdatedAt(DateUtils.getEpochNow());

            if (tx.getAccount().getType() == AccountType.CREDIT_CARD) {
                List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(tx.getId());
                for (InstallmentPlan inst : installments) {
                    inst.setAmount(newAmount);
                    inst.setUpdatedAt(DateUtils.getEpochNow());

                    Invoices invoice = invoicesToUpdate.getOrDefault(inst.getInvoices().getId(), inst.getInvoices());
                    invoice.setAmount(invoice.getAmount().add(difference));
                    invoicesToUpdate.put(invoice.getId(), invoice);
                }
                installmentPlanService.saveAll(installments);
            }
        }
        repository.saveAll(futureUnpaidTx);

        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
    }

    @Override
    @Transactional
    public void generateProjectionsForRule(RecurrenceRule rule, LocalDate limitDate) {
        Long maxDateEpoch = repository.findMaxDateByRuleId(rule.getId());
        LocalDate nextDate = (maxDateEpoch != null) ? calculateNextDate(DateUtils.epochToLocalDate(maxDateEpoch), rule.getFrequency()) : DateUtils.epochToLocalDate(rule.getStartDate());
        long endDateEpoch = rule.getEndDate() != null ? rule.getEndDate() : Long.MAX_VALUE;

        while (!nextDate.isAfter(limitDate) && DateUtils.localDateToEpoch(nextDate) <= endDateEpoch) {
            long epochNextDate = DateUtils.localDateToEpoch(nextDate);

            if (rule.getType() == TransactionType.TRANSFERENCIA) {
                Transactions transferOut = createTransactionFromRule(rule, TransactionType.TRANSFERENCIA_SAIDA, epochNextDate, rule.getAccount());
                Transactions transferIn = createTransactionFromRule(rule, TransactionType.TRANSFERENCIA_ENTRADA, epochNextDate, rule.getTargetAccount());
                transferIn.setParentTransaction(transferOut);
                repository.saveAll(List.of(transferOut, transferIn));
            } else if (rule.getAccount().getType() == AccountType.CREDIT_CARD) {
                createProjectedCreditCardExpense(rule, epochNextDate);
            } else {
                Transactions tx = createTransactionFromRule(rule, rule.getType(), epochNextDate, rule.getAccount());
                repository.save(tx);
            }
            nextDate = calculateNextDate(nextDate, rule.getFrequency());
        }
    }

    private void deleteInstallmentsAndRestoreInvoice(UUID purchaseId, long dateNow) {
        List<InstallmentPlan> installments = installmentPlanService.findByPurchaseId(purchaseId);
        Map<UUID, Invoices> invoicesToUpdate = new HashMap<>();

        for (InstallmentPlan inst : installments) {
            if (inst.getDeletedAt() == null) {
                inst.setDeletedAt(dateNow);

                Invoices invoice = invoicesToUpdate.getOrDefault(inst.getInvoices().getId(), inst.getInvoices());
                invoice.setAmount(invoice.getAmount().subtract(inst.getAmount()));
                invoicesToUpdate.put(invoice.getId(), invoice);
            }
        }
        installmentPlanService.saveAll(installments);

        if (!invoicesToUpdate.isEmpty()) {
            invoicesService.saveAll(invoicesToUpdate.values().stream().toList());
        }
    }

    private void createProjectedCreditCardExpense(RecurrenceRule rule, long epochNextDate) {
        CreditCard card = creditCardService.findByAccountId(rule.getAccount().getId());

        Transactions purchaseTransaction = createTransactionFromRule(rule, rule.getType(), epochNextDate, rule.getAccount());
        purchaseTransaction = repository.save(purchaseTransaction);

        LocalDateTime dataCompra = DateUtils.epochToLocalDateTime(epochNextDate);
        LocalDateTime dataVencimentoFatura = helper.calculateInvoiceDate(dataCompra, card.getCloseDay(), card.getBestDay());

        int invMonth = dataVencimentoFatura.getMonthValue();
        int invYear = dataVencimentoFatura.getYear();

        Invoices invoice = invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invMonth, invYear)
                .orElseGet(() -> invoicesService.save(Invoices.builder()
                        .id(ID.generate()).month(invMonth).year(invYear).amount(BigDecimal.ZERO)
                        .expirationDate(DateUtils.localDateTimeToEpoch(dataVencimentoFatura)).paid(false).enabled(true)
                        .createdAt(DateUtils.getEpochNow()).creditCard(card).user(rule.getUser()).build()));

        invoice.setAmount(invoice.getAmount().add(rule.getBaseAmount()));
        invoicesService.save(invoice);

        InstallmentPlan installment = InstallmentPlan.builder()
                .id(ID.generate()).name(rule.getName()).type(TransactionType.DESPESA.name()).amount(rule.getBaseAmount())
                .totalInstallmentsPlan(1).currentInstallment(1).fixed(true).paid(false).purchaseId(purchaseTransaction.getId())
                .enabled(true).createdAt(DateUtils.getEpochNow()).date(invoice.getExpirationDate()).invoices(invoice).user(rule.getUser()).build();

        installmentPlanService.save(installment);
    }

    private Transactions createTransactionFromRule(RecurrenceRule rule, TransactionType type, long date, Accounts account) {
        return Transactions.builder().id(ID.generate()).name(rule.getName()).description(rule.getDescription())
                .type(type).amount(rule.getBaseAmount()).date(date).paid(false).fixed(true).enabled(true)
                .createdAt(DateUtils.getEpochNow()).account(account).category(rule.getCategory()).user(rule.getUser()).recurrenceRule(rule).build();
    }

    private LocalDate calculateNextDate(LocalDate current, RecurrenceFrequency freq) {
        return switch (freq) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }
}