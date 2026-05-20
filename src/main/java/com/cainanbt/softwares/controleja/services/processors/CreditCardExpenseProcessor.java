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
import com.cainanbt.softwares.controleja.services.AccountsService;
import com.cainanbt.softwares.controleja.services.CreditCardService;
import com.cainanbt.softwares.controleja.services.InstallmentPlanService;
import com.cainanbt.softwares.controleja.services.InvoicesService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CreditCardExpenseProcessor implements TransactionProcessor {

    private final CreditCardService creditCardService;
    private final TransactionRepository repository;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final AccountsService accountsService;
    private final TransactionHelper helper;

    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return account.getType() == AccountType.CREDIT_CARD && dto.getType() == TransactionType.DESPESA;
    }

    @Override
    public Transactions process(TransactionDTO dto, Accounts account, Category category, Users user) {
        if (dto.getType() != TransactionType.DESPESA) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ONLY_EXPENSE);
        }

        long dateNow = DateUtils.getEpochNow();
        CreditCard card = creditCardService.findByAccountId(account.getId());

        card.consumeLimit(dto.getAmount());
        creditCardService.updateLimit(card);

        // CORREÇÃO: Registrando a Regra de Recorrência se o usuário marcar "Fixo" no Cartão de Crédito
        RecurrenceRule rule = null;
        if (Boolean.TRUE.equals(dto.getIsFixed()) && dto.getRecurrenceFrequency() != null) {
            rule = helper.createRecurrenceRule(dto, TransactionType.DESPESA, dateNow, user, account, null, category);
        }

        Transactions purchaseTransaction = helper.createBaseTransactionBuilder(dto, account, category, user)
                .paid(false)
                .creditCard(card)
                .recurrenceRule(rule) // ANEXANDO A REGRA AQUI!
                .build();
        purchaseTransaction = repository.save(purchaseTransaction);

        int parcelas = (dto.getInstallments() == null || dto.getInstallments() < 1) ? 1 : dto.getInstallments();

        BigDecimal valorParcela = dto.getAmount().divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal diferenca = dto.getAmount().subtract(valorParcela.multiply(BigDecimal.valueOf(parcelas)));

        LocalDateTime dataCompra = DateUtils.epochToLocalDateTime(dto.getDate());

        for (int i = 0; i < parcelas; i++) {
            BigDecimal valorDestaParcela = (i == 0) ? valorParcela.add(diferenca) : valorParcela;

            LocalDateTime dataVencimentoFatura = helper.calculateInvoiceDate(dataCompra.plusMonths(i), card.getCloseDay(), card.getBestDay());
            int invMonth = dataVencimentoFatura.getMonthValue();
            int invYear = dataVencimentoFatura.getYear();

            Invoices invoice = invoicesService.findByCreditCardIdAndMonthAndYear(card.getId(), invMonth, invYear)
                    .orElseGet(() -> invoicesService.save(Invoices.builder()
                            .id(ID.generate()).month(invMonth).year(invYear)
                            .amount(BigDecimal.ZERO).expirationDate(DateUtils.localDateTimeToEpoch(dataVencimentoFatura))
                            .paid(false).enabled(true).createdAt(dateNow)
                            .creditCard(card).user(user)
                            .build()));

            invoice.setAmount(invoice.getAmount().add(valorDestaParcela));
            invoicesService.save(invoice);

            InstallmentPlan installment = InstallmentPlan.builder()
                    .id(ID.generate())
                    .name(dto.getName() + (parcelas > 1 ? " (" + (i + 1) + "/" + parcelas + ")" : ""))
                    .type(TransactionType.DESPESA.name()).amount(valorDestaParcela)
                    .totalInstallmentsPlan(parcelas).currentInstallment(i + 1)
                    .fixed(dto.getIsFixed() != null ? dto.getIsFixed() : false)
                    .paid(false).purchaseId(purchaseTransaction.getId())
                    .enabled(true).createdAt(dateNow).date(invoice.getExpirationDate())
                    .invoices(invoice).user(user)
                    .build();

            installmentPlanService.save(installment);
        }

        account.debit(dto.getAmount());
        accountsService.update(account);

        return purchaseTransaction;
    }
}
