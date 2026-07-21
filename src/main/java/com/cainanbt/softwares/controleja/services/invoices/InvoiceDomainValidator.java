package com.cainanbt.softwares.controleja.services.invoices;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Reúne regras de integridade que protegem faturas, parcelas e vínculos financeiros.
 */
@Component
public class InvoiceDomainValidator {

    /**
     * Garante que a fatura pertence ao usuário autenticado.
     */
    public void validateInvoiceOwner(Invoices invoice, Users currentUser) {
        if (invoice.getUser() == null || !invoice.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException("Acesso Negado", "Fatura não pertence ao usuário autenticado.");
        }
    }

    /**
     * Bloqueia alteração em faturas pagas, desabilitadas ou removidas.
     */
    public void validateEditableInvoice(Invoices invoice) {
        if (invoice == null
                || Boolean.TRUE.equals(invoice.getPaid())
                || Boolean.FALSE.equals(invoice.getEnabled())
                || invoice.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar fatura paga, fechada ou bloqueada para edição.");
        }
    }

    /**
     * Bloqueia alteração em parcelas pagas ou removidas.
     */
    public void validateEditableInstallment(InstallmentPlan installment) {
        if (installment == null || Boolean.TRUE.equals(installment.getPaid()) || installment.getDeletedAt() != null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Não é possível alterar parcela paga ou removida.");
        }
    }

    /**
     * Valida a entrada de estorno antes de buscar e alterar parcelas da fatura.
     */
    public void validateRefundRequest(RefundRequestDTO request) {
        if (request == null || request.getInstallmentId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Parcela não informada.");
        }
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do estorno deve ser maior que zero.");
        }
    }

    /**
     * Valida a entrada de adiantamento antes de mover parcelas entre faturas.
     */
    public void validateAdvanceRequest(AdvanceRequestDTO request) {
        if (request == null || request.getPurchaseId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Compra não informada.");
        }
        if (request.getQuantityToAdvance() != null && request.getQuantityToAdvance() <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A quantidade de parcelas para adiantar deve ser maior que zero.");
        }
        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O desconto não pode ser negativo.");
        }
    }

    /**
     * Valida a entrada de pagamento antes de criar transações e movimentar saldos.
     */
    public void validatePaymentRequest(InvoicePaymentRequestDTO request) {
        if (request == null || request.getAccountId() == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Conta de pagamento não informada.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "O valor do pagamento deve ser maior que zero.");
        }
    }

    /**
     * Confirma se a parcela futura pertence ao usuário, ao cartão da fatura atual e ainda está pendente.
     */
    public boolean isAdvanceableFutureInstallment(InstallmentPlan installment, Invoices currentInvoice, Users currentUser) {
        if (installment.getDeletedAt() != null
                || Boolean.TRUE.equals(installment.getPaid())
                || Boolean.FALSE.equals(installment.getEnabled())) {
            return false;
        }
        if (installment.getAmount() == null || installment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (installment.getUser() == null || !installment.getUser().getId().equals(currentUser.getId())) {
            return false;
        }
        Invoices futureInvoice = installment.getInvoices();
        if (futureInvoice == null
                || futureInvoice.getDeletedAt() != null
                || Boolean.TRUE.equals(futureInvoice.getPaid())
                || Boolean.FALSE.equals(futureInvoice.getEnabled())
                || futureInvoice.getCreditCard() == null
                || currentInvoice.getCreditCard() == null) {
            return false;
        }
        if (!futureInvoice.getCreditCard().getId().equals(currentInvoice.getCreditCard().getId())) {
            return false;
        }
        return futureInvoice.getExpirationDate() != null
                && currentInvoice.getExpirationDate() != null
                && futureInvoice.getExpirationDate() > currentInvoice.getExpirationDate();
    }

    /**
     * Garante que a fatura possui cartão ativo antes de mexer em limite ou conta vinculada.
     */
    public CreditCard requireInvoiceCard(Invoices invoice) {
        CreditCard card = invoice.getCreditCard();
        if (card == null || card.getDeletedAt() != null) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Cartão vinculado à fatura não encontrado.");
        }
        return card;
    }

    /**
     * Garante que o cartão possui conta contábil para receber o lançamento interno.
     */
    public Accounts requireCardAccount(CreditCard card) {
        Accounts account = card.getAccounts();
        if (account == null || account.getDeletedAt() != null) {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Conta vinculada ao cartão não encontrada.");
        }
        return account;
    }

    /**
     * Bloqueia uso de conta cartão como origem de pagamento de fatura.
     */
    public void validatePaymentSourceAccount(Accounts sourceAccount, Users currentUser) {
        if (!sourceAccount.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.NO_PERMISSION_ACCOUNT);
        }
        if (sourceAccount.getType() == AccountType.CREDIT_CARD) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A conta de pagamento não pode ser uma conta de cartão de crédito.");
        }
    }
}
