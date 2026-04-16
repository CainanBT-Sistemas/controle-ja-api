package com.cainanbt.softwares.controleja.services.web;

import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceRequestDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.AdvanceablePurchaseDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.InvoiceDetailsDTO;
import com.cainanbt.softwares.controleja.dtos.invoices.RefundRequestDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoicesWebService {
    Optional<InvoiceDetailsDTO> getInvoiceDetails(UUID cardId, Integer month, Integer year);

    List<AdvanceablePurchaseDTO> getAdvanceablePurchases(UUID cardId, Integer month, Integer year);

    void processRefund(UUID invoiceId, RefundRequestDTO request);

    void advanceInstallments(UUID invoiceId, AdvanceRequestDTO request);
}
