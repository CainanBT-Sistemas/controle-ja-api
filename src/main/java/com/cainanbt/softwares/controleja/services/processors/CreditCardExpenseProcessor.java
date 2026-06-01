package com.cainanbt.softwares.controleja.services.processors;

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
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditCardExpenseProcessor implements TransactionProcessor {

    private final CreditCardService creditCardService;
    private final TransactionRepository repository;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final TransactionHelper helper;

    /**
     * Informa se este processor deve tratar uma despesa feita em conta espelho de cartão.
     */
    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return account.getType() == AccountType.CREDIT_CARD && dto.getType() == TransactionType.DESPESA;
    }

    /**
     * Registra a compra no cartão, consome limite e cria as parcelas nas faturas corretas.
     */
    @Override
    public Transactions process(TransactionDTO dto, Accounts account, Category category, Users user) {
        validateCreditCardExpense(dto);
        long dateNow = DateUtils.getEpochNow();
        CreditCard card = creditCardService.findByAccountId(account.getId());

        card.consumeLimit(dto.getAmount());
        creditCardService.updateLimit(card);

        Transactions purchaseTransaction = repository.save(buildPurchaseTransaction(dto, account, category, user, card, dateNow));
        createInstallments(dto, user, card, purchaseTransaction, dateNow);

        log.info("Credit card purchase created: transactionId={}, cardId={}, amount={}",
                purchaseTransaction.getId(), card.getId(), dto.getAmount());
        return purchaseTransaction;
    }

    private void validateCreditCardExpense(TransactionDTO dto) {
        if (dto.getType() != TransactionType.DESPESA) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ONLY_EXPENSE);
        }
    }

    /**
     * Monta a transação pai da compra, incluindo recorrência quando aplicável.
     */
    private Transactions buildPurchaseTransaction(
            TransactionDTO dto,
            Accounts account,
            Category category,
            Users user,
            CreditCard card,
            long dateNow) {
        RecurrenceRule rule = null;
        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            rule = helper.createRecurrenceRule(dto, TransactionType.DESPESA, dateNow, user, account, null, category);
        }

        return helper.createBaseTransactionBuilder(dto, account, category, user)
                .paid(false)
                .creditCard(card)
                .recurrenceRule(rule)
                .build();
    }

    /**
     * Cria cada parcela e atualiza a fatura de destino correspondente.
     */
    private void createInstallments(TransactionDTO dto, Users user, CreditCard card, Transactions purchaseTransaction, long dateNow) {
        int installments = resolveInstallments(dto);
        List<BigDecimal> installmentAmounts = splitAmount(dto.getAmount(), installments);
        LocalDateTime purchaseDate = DateUtils.epochToLocalDateTime(dto.getDate());

        for (int index = 0; index < installments; index++) {
            Invoices invoice = resolveInvoice(card, user, purchaseDate.plusMonths(index), dateNow);
            BigDecimal installmentAmount = installmentAmounts.get(index);

            invoice.setAmount(invoice.getAmount().add(installmentAmount));
            invoicesService.save(invoice);
            installmentPlanService.save(buildInstallment(dto, user, purchaseTransaction, invoice, installmentAmount, index, installments, dateNow));
        }
    }

    /**
     * Normaliza a quantidade de parcelas para pelo menos uma parcela.
     */
    private int resolveInstallments(TransactionDTO dto) {
        return dto.getInstallments() == null || dto.getInstallments() < 1 ? 1 : dto.getInstallments();
    }

    /**
     * Divide o valor total sem perder centavos, deixando a diferença na primeira parcela.
     */
    private List<BigDecimal> splitAmount(BigDecimal amount, int installments) {
        BigDecimal baseAmount = amount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        BigDecimal difference = amount.subtract(baseAmount.multiply(BigDecimal.valueOf(installments)));

        List<BigDecimal> values = new ArrayList<>(installments);
        for (int index = 0; index < installments; index++) {
            values.add(index == 0 ? baseAmount.add(difference) : baseAmount);
        }
        return values;
    }

    /**
     * Busca ou cria a fatura correta conforme data da compra, fechamento e vencimento do cartão.
     */
    private Invoices resolveInvoice(CreditCard card, Users user, LocalDateTime installmentDate, long dateNow) {
        LocalDateTime invoiceDueDate = helper.calculateInvoiceDate(installmentDate, card.getCloseDay(), card.getBestDay());
        int invoiceMonth = invoiceDueDate.getMonthValue();
        int invoiceYear = invoiceDueDate.getYear();

        return invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invoiceMonth, invoiceYear)
                .orElseGet(() -> invoicesService.save(Invoices.builder()
                        .id(ID.generate())
                        .month(invoiceMonth)
                        .year(invoiceYear)
                        .amount(BigDecimal.ZERO)
                        .expirationDate(DateUtils.localDateTimeToEpoch(invoiceDueDate))
                        .paid(false)
                        .enabled(true)
                        .createdAt(dateNow)
                        .creditCard(card)
                        .user(user)
                        .build()));
    }

    /**
     * Monta a parcela persistida na fatura mantendo vínculo com a transação pai da compra.
     */
    private InstallmentPlan buildInstallment(
            TransactionDTO dto,
            Users user,
            Transactions purchaseTransaction,
            Invoices invoice,
            BigDecimal installmentAmount,
            int index,
            int installments,
            long dateNow) {
        return InstallmentPlan.builder()
                .id(ID.generate())
                .name(dto.getName() + (installments > 1 ? " (" + (index + 1) + "/" + installments + ")" : ""))
                .type(TransactionType.DESPESA.name())
                .amount(installmentAmount)
                .totalInstallmentsPlan(installments)
                .currentInstallment(index + 1)
                .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                .paid(false)
                .purchaseId(purchaseTransaction.getId())
                .enabled(true)
                .createdAt(dateNow)
                .date(invoice.getExpirationDate())
                .invoices(invoice)
                .user(user)
                .build();
    }
}
