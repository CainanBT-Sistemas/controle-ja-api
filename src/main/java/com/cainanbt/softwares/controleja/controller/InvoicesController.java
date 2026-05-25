package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoicePaymentRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;
import com.cainanbt.softwares.controleja.services.web.InvoicesWebService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/controle_ja_api/v1/invoices")
@RequiredArgsConstructor
public class InvoicesController {
    private final InvoicesWebService service;

    @GetMapping("/card/{cardId}/month/{month}/year/{year}")
    public ResponseEntity<InvoiceDetailsDTO> getInvoiceDetails(@PathVariable UUID cardId, @PathVariable Integer month, @PathVariable Integer year) {
        Optional<InvoiceDetailsDTO> invoiceDetails = service.getInvoiceDetails(cardId, month, year);
        if (invoiceDetails.isPresent()) {
            return ResponseEntity.ok(invoiceDetails.get());
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/card/{cardId}/month/{month}/year/{year}/advanceable")
    public ResponseEntity<List<com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO>> getAdvanceable(@PathVariable UUID cardId, @PathVariable Integer month, @PathVariable Integer year) {
        List<AdvanceablePurchaseDTO> list = service.getAdvanceablePurchases(cardId, month, year);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{invoiceId}/refund")
    public ResponseEntity<Void> processRefund(@PathVariable UUID invoiceId, @RequestBody RefundRequestDTO request) {
        service.processRefund(invoiceId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{invoiceId}/advance")
    public ResponseEntity<Void> advanceInstallments(@PathVariable UUID invoiceId, @RequestBody AdvanceRequestDTO request) {
        service.advanceInstallments(invoiceId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<InvoiceDetailsDTO> processPayment(@PathVariable UUID invoiceId, @RequestBody InvoicePaymentRequestDTO request) {
        return ResponseEntity.ok(service.processPayment(invoiceId, request));
    }
}
