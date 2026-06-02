package com.cainanbt.softwares.controleja.services.dashboard;

import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardAlertDTO;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.entities.Transactions;

/**
 * Converte entidades financeiras para os alertas exibidos no dashboard.
 */
public class DashboardAlertMapper {

    /**
     * Monta alerta de conta a pagar ou receber a partir de uma transacao pendente.
     */
    public DashboardAlertDTO fromTransaction(Transactions transaction) {
        return DashboardAlertDTO.builder()
                .id(transaction.getId())
                .description(transaction.getName())
                .amount(transaction.getAmount())
                .dueDate(transaction.getDate())
                .icon(transaction.getCategory() != null ? transaction.getCategory().getIcon() : null)
                .color(transaction.getCategory() != null ? transaction.getCategory().getColor() : null)
                .type(transaction.getType().name())
                .build();
    }

    /**
     * Monta alerta de fatura pendente preservando referencia ao cartao.
     */
    public DashboardAlertDTO fromInvoice(Invoices invoice) {
        return DashboardAlertDTO.builder()
                .id(invoice.getId())
                .referenceId(invoice.getCreditCard() != null ? invoice.getCreditCard().getId() : null)
                .description("Fatura " + (invoice.getCreditCard() != null ? invoice.getCreditCard().getName() : ""))
                .amount(invoice.getAmount())
                .dueDate(invoice.getExpirationDate())
                .icon(invoice.getCreditCard() != null ? invoice.getCreditCard().getIcon() : null)
                .color(invoice.getCreditCard() != null ? invoice.getCreditCard().getColor() : null)
                .type("FATURA")
                .month(invoice.getMonth())
                .year(invoice.getYear())
                .build();
    }
}
