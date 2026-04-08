package com.cainanbt.softwares.controleja.services.processors;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Category;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
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

import java.util.List;

@Component
@RequiredArgsConstructor
public class InvoicePaymentProcessor implements TransactionProcessor {

    private final AccountsService accountsService;
    private final CreditCardService creditCardService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final TransactionRepository repository;

    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return dto.getType() == TransactionType.PAGAMENTO_FATURA;
    }

    @Override
    public Transactions process(TransactionDTO dto, Accounts sourceAccount, Category category, Users user) {
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_MISSING_TARGET);
        }
        Accounts cardAccount = accountsService.findById(dto.getTargetAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ACCOUNT_NOT_FOUND));

        if (cardAccount.getType() != AccountType.CREDIT_CARD) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_TARGET_NOT_CARD);
        }

        long dateNow = DateUtils.getEpochNow();

        Transactions paymentOut = Transactions.builder()
                .id(ID.generate()).name(dto.getName()).description(dto.getDescription())
                .type(TransactionType.PAGAMENTO_FATURA).amount(dto.getAmount())
                .fixed(false).paid(dto.getPaid()).enabled(true)
                .createdAt(dateNow).date(dto.getDate())
                .account(sourceAccount).category(category).user(user)
                .build();

        Transactions paymentIn = Transactions.builder()
                .id(ID.generate()).name("Recebimento de Fatura").description(dto.getDescription())
                .type(TransactionType.TRANSFERENCIA_ENTRADA).amount(dto.getAmount())
                .fixed(false).paid(dto.getPaid()).enabled(true)
                .createdAt(dateNow).date(dto.getDate())
                .account(cardAccount).category(category).user(user)
                .parentTransaction(paymentOut)
                .build();

        repository.saveAll(List.of(paymentOut, paymentIn));

        if (Boolean.TRUE.equals(dto.getPaid())) {
            sourceAccount.debit(dto.getAmount());
            accountsService.update(sourceAccount);
            cardAccount.credit(dto.getAmount());
            accountsService.update(cardAccount);

            CreditCard card = creditCardService.findByAccountId(cardAccount.getId());
            card.restoreLimit(dto.getAmount());
            creditCardService.updateLimit(card);

            if (dto.getTargetInvoiceId() != null) {
                Invoices invoiceToPay = invoicesService.findByIdOrThrow(dto.getTargetInvoiceId());
                invoiceToPay.setPaid(true);
                invoiceToPay.setTransaction(paymentOut);
                invoicesService.save(invoiceToPay);

                List<InstallmentPlan> installmentPlans = installmentPlanService.findByInvoiceId(invoiceToPay.getId());
                installmentPlans.forEach(inst -> inst.setPaid(true));
                installmentPlanService.saveAll(installmentPlans);
            }
        }
        return paymentOut;
    }
}