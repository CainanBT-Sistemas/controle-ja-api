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
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDateService;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceDomainValidator;
import com.cainanbt.softwares.controleja.services.invoices.InvoiceTotalsCalculator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoicePaymentProcessor implements TransactionProcessor {

    private final InvoiceDateService invoiceDateService = new InvoiceDateService();
    private final InvoiceTotalsCalculator invoiceTotalsCalculator = new InvoiceTotalsCalculator();
    private final InvoiceDomainValidator invoiceDomainValidator = new InvoiceDomainValidator();

    private final AccountsService accountsService;
    private final CreditCardService creditCardService;
    private final InvoicesService invoicesService;
    private final InstallmentPlanService installmentPlanService;
    private final TransactionRepository repository;

    @Override
    public boolean supports(TransactionDTO dto, Accounts account) {
        return dto.getType() == TransactionType.PAGAMENTO_FATURA;
    }

    /**
     * Cria as transações contábeis de pagamento de fatura e atualiza saldos quando o pagamento já está confirmado.
     */
    @Override
    public Transactions process(TransactionDTO dto, Accounts sourceAccount, Category category, Users user) {
        invoiceDomainValidator.validatePaymentSourceAccount(sourceAccount, user);
        if (dto.getTargetAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_MISSING_TARGET);
        }
        Accounts cardAccount = accountsService.findById(dto.getTargetAccountId())
                .orElseThrow(() -> new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.CARD_ACCOUNT_NOT_FOUND));

        if (cardAccount.getType() != AccountType.CREDIT_CARD) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, ConstsMessages.INVOICE_TARGET_NOT_CARD);
        }
        if (!cardAccount.getUser().getId().equals(user.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }

        Invoices invoiceToPay = null;
        CreditCard card = creditCardService.findByAccountId(cardAccount.getId());
        if (dto.getTargetInvoiceId() != null) {
            invoiceToPay = invoicesService.findByIdOrThrow(dto.getTargetInvoiceId());
            if (!invoiceToPay.getUser().getId().equals(user.getId())) {
                throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, "Fatura não pertence ao usuário autenticado.");
            }
            invoiceDomainValidator.validateEditableInvoice(invoiceToPay);
            if (invoiceToPay.getCreditCard() == null
                    || !invoiceToPay.getCreditCard().getAccounts().getId().equals(cardAccount.getId())) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A conta de destino não pertence ao cartão da fatura.");
            }
            if (invoiceToPay.getAmount() != null && dto.getAmount().compareTo(invoiceToPay.getAmount()) > 0) {
                throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O pagamento não pode ser maior que o saldo em aberto.");
            }
        }

        long dateNow = DateUtils.getEpochNow();

        Transactions paymentOut = Transactions.builder()
                .id(ID.generate()).name(dto.getName()).description(dto.getDescription())
                .type(TransactionType.PAGAMENTO_FATURA).amount(dto.getAmount())
                .fixed(false).paid(dto.getPaid()).enabled(true)
                .createdAt(dateNow).date(dto.getDate())
                .account(sourceAccount).category(category).user(user)
                .targetInvoice(invoiceToPay).creditCard(card)
                .build();

        Transactions paymentIn = Transactions.builder()
                .id(ID.generate()).name("Recebimento de Fatura").description(dto.getDescription())
                .type(TransactionType.TRANSFERENCIA_ENTRADA).amount(dto.getAmount())
                .fixed(false).paid(dto.getPaid()).enabled(true)
                .createdAt(dateNow).date(dto.getDate())
                .account(cardAccount).category(category).user(user)
                .targetInvoice(invoiceToPay).creditCard(card)
                .parentTransaction(paymentOut)
                .build();

        repository.saveAll(List.of(paymentOut, paymentIn));

        if (Boolean.TRUE.equals(dto.getPaid())) {
            sourceAccount.debit(dto.getAmount());
            accountsService.update(sourceAccount);
            cardAccount.credit(dto.getAmount());
            accountsService.update(cardAccount);

            card.restoreLimit(dto.getAmount());
            creditCardService.updateLimit(card);

            if (invoiceToPay != null) {
                InstallmentPlan paymentCredit = InstallmentPlan.builder()
                        .id(ID.generate())
                        .date(dto.getDate())
                        .name("Pagamento Recebido")
                        .description(dto.getDescription())
                        .type(TransactionType.RECEITA.name())
                        .amount(dto.getAmount().abs().negate())
                        .totalInstallmentsPlan(1)
                        .currentInstallment(1)
                        .fixed(false)
                        .paid(true)
                        .purchaseId(paymentOut.getId())
                        .enabled(true)
                        .createdAt(DateUtils.getEpochNow())
                        .user(user)
                        .invoices(invoiceToPay)
                        .build();

                installmentPlanService.save(paymentCredit);

                invoiceToPay.setAmount(invoiceTotalsCalculator.valueOrZero(invoiceToPay.getAmount()).subtract(dto.getAmount()));

                if ((invoiceToPay.getAmount() == null || invoiceToPay.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                        && !invoiceDateService.isInvoiceOpenWindow(invoiceToPay)) {
                    invoiceToPay.setPaid(true);
                    invoiceToPay.setAmount(BigDecimal.ZERO);

                    List<InstallmentPlan> installmentPlans = installmentPlanService.findByInvoiceId(invoiceToPay.getId());
                    installmentPlans.forEach(inst -> inst.setPaid(true));
                    installmentPlanService.saveAll(installmentPlans);
                }

                invoiceToPay.setTransaction(paymentOut);
                invoicesService.save(invoiceToPay);
                log.info("Invoice payment transaction processed: invoiceId={}, transactionId={}, amount={}", invoiceToPay.getId(), paymentOut.getId(), dto.getAmount());
            }
        }
        return paymentOut;
    }
}
