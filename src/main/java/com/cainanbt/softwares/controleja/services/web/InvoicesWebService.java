package com.cainanbt.softwares.controleja.services.web;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.enums.OperationScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoicesWebService {
    /**
     * Retorna detalhes da fatura real ou uma fatura calculada para cartão/mês/ano sem lançamento persistido.
     */
    Optional<InvoiceDetailsDTO> getInvoiceDetails(UUID cardId, Integer month, Integer year);

    /**
     * Lista compras futuras elegíveis para adiantamento na fatura selecionada.
     */
    List<AdvanceablePurchaseDTO> getAdvanceablePurchases(UUID cardId, Integer month, Integer year);

    /**
     * Aplica crédito de estorno sobre uma parcela pertencente à fatura.
     */
    void processRefund(UUID invoiceId, RefundRequestDTO request);

    /**
     * Move parcelas futuras da mesma compra para a fatura informada.
     */
    void advanceInstallments(UUID invoiceId, AdvanceRequestDTO request);

    /**
     * Atualiza item/parcela da fatura respeitando o escopo informado.
     */
    InvoiceDetailsDTO updateInvoiceItem(UUID invoiceId, UUID installmentId, TransactionDTO request, OperationScope operationScope);

    /**
     * Remove item/parcela da fatura respeitando o escopo informado.
     */
    InvoiceDetailsDTO deleteInvoiceItem(UUID invoiceId, UUID installmentId, OperationScope operationScope);

    /**
     * Cancela a compra inteira vinculada à fatura quando nenhuma parcela está paga.
     */
    InvoiceDetailsDTO cancelPurchase(UUID invoiceId, UUID purchaseId);

    /**
     * Registra pagamento de fatura e retorna a fatura recalculada.
     */
    InvoiceDetailsDTO processPayment(UUID invoiceId, InvoicePaymentRequestDTO request);

    /**
     * Cancela pagamento de fatura usando o id da transação ou do item de pagamento.
     */
    InvoiceDetailsDTO cancelPayment(UUID paymentTransactionId);
}
