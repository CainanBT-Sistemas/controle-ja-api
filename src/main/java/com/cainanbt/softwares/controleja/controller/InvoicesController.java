package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.enums.OperationScope;
import com.cainanbt.softwares.controleja.services.web.InvoicesWebService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/controle_ja_api/v1/invoices")
@RequiredArgsConstructor
public class InvoicesController {
    private final InvoicesWebService service;

    /**
     * Consulta os dados consolidados de uma fatura de cartão em um mês/ano.
     */
    @GetMapping("/card/{cardId}/month/{month}/year/{year}")
    public ResponseEntity<InvoiceDetailsDTO> getInvoiceDetails(@PathVariable UUID cardId, @PathVariable Integer month, @PathVariable Integer year) {
        return service.getInvoiceDetails(cardId, month, year)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Lista compras futuras que ainda podem ser adiantadas para a fatura informada.
     */
    @GetMapping("/card/{cardId}/month/{month}/year/{year}/advanceable")
    public ResponseEntity<List<AdvanceablePurchaseDTO>> getAdvanceable(@PathVariable UUID cardId, @PathVariable Integer month, @PathVariable Integer year) {
        List<AdvanceablePurchaseDTO> list = service.getAdvanceablePurchases(cardId, month, year);
        return ResponseEntity.ok(list);
    }

    /**
     * Registra um estorno parcial ou total em uma parcela da fatura.
     */
    @PostMapping("/{invoiceId}/refund")
    public ResponseEntity<Void> processRefund(@PathVariable UUID invoiceId, @Valid @RequestBody RefundRequestDTO request) {
        service.processRefund(invoiceId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Move parcelas futuras da mesma compra para a fatura atual.
     */
    @PostMapping("/{invoiceId}/advance")
    public ResponseEntity<Void> advanceInstallments(@PathVariable UUID invoiceId, @Valid @RequestBody AdvanceRequestDTO request) {
        service.advanceInstallments(invoiceId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Corrige um adiantamento registrado por engano enquanto a fatura ainda permite reversao interna.
     */
    @PostMapping("/{invoiceId}/advances/{operationId}/correction")
    public ResponseEntity<InvoiceDetailsDTO> correctAdvance(
            @PathVariable UUID invoiceId,
            @PathVariable UUID operationId) {
        return ResponseEntity.ok(service.correctAdvance(invoiceId, operationId));
    }

    /**
     * Edita um item/parcela da fatura respeitando o escopo solicitado.
     */
    @PutMapping("/{invoiceId}/items/{installmentId}")
    public ResponseEntity<InvoiceDetailsDTO> updateInvoiceItem(
            @PathVariable UUID invoiceId,
            @PathVariable UUID installmentId,
            @Valid @RequestBody TransactionDTO request,
            @RequestParam(defaultValue = "ONLY_THIS") OperationScope operationScope) {
        return ResponseEntity.ok(service.updateInvoiceItem(invoiceId, installmentId, request, operationScope));
    }

    /**
     * Remove um item/parcela da fatura respeitando o escopo solicitado.
     */
    @DeleteMapping("/{invoiceId}/items/{installmentId}")
    public ResponseEntity<InvoiceDetailsDTO> deleteInvoiceItem(
            @PathVariable UUID invoiceId,
            @PathVariable UUID installmentId,
            @RequestParam(defaultValue = "ONLY_THIS") OperationScope operationScope) {
        return ResponseEntity.ok(service.deleteInvoiceItem(invoiceId, installmentId, operationScope));
    }

    /**
     * Cancela a compra inteira vinculada à fatura quando não há parcelas pagas.
     */
    @DeleteMapping("/{invoiceId}/purchases/{purchaseId}")
    public ResponseEntity<InvoiceDetailsDTO> cancelPurchase(
            @PathVariable UUID invoiceId,
            @PathVariable UUID purchaseId) {
        return ResponseEntity.ok(service.cancelPurchase(invoiceId, purchaseId));
    }

    /**
     * Quita a fatura usando uma conta de pagamento e cria os lançamentos contábeis vinculados.
     */
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<InvoiceDetailsDTO> processPayment(@PathVariable UUID invoiceId, @Valid @RequestBody InvoicePaymentRequestDTO request) {
        return ResponseEntity.ok(service.processPayment(invoiceId, request));
    }

    /**
     * Cancela um pagamento de fatura pelo id da transação ou pelo item de pagamento exibido na fatura.
     */
    @PostMapping("/payments/{paymentTransactionId}/cancel")
    public ResponseEntity<InvoiceDetailsDTO> cancelPayment(@PathVariable UUID paymentTransactionId) {
        return ResponseEntity.ok(service.cancelPayment(paymentTransactionId));
    }
}
