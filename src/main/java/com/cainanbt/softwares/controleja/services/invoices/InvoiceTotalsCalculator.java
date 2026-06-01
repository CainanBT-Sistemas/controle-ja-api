package com.cainanbt.softwares.controleja.services.invoices;

import com.cainanbt.softwares.controleja.entities.InstallmentPlan;
import com.cainanbt.softwares.controleja.entities.Invoices;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Centraliza os cálculos monetários de fatura para evitar regras duplicadas em controllers e services.
 */
@Component
public class InvoiceTotalsCalculator {

    /**
     * Calcula total comprado, total pago e saldo aberto a partir dos itens ativos da fatura.
     */
    public InvoiceTotalsSummary calculate(List<InstallmentPlan> items) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;

        for (InstallmentPlan item : items) {
            if (item.getAmount() == null) continue;
            if (isPaymentItem(item)) {
                paidAmount = paidAmount.add(item.getAmount().abs());
            } else {
                totalAmount = totalAmount.add(item.getAmount());
            }
        }

        BigDecimal openAmount = totalAmount.subtract(paidAmount);
        if (openAmount.compareTo(BigDecimal.ZERO) < 0) {
            openAmount = BigDecimal.ZERO;
        }
        return new InvoiceTotalsSummary(totalAmount, paidAmount, openAmount);
    }

    /**
     * Calcula totais para consulta, preservando faturas antigas que ainda têm valor salvo mas não têm itens detalhados.
     */
    public InvoiceTotalsSummary calculateForDetails(Invoices invoice, List<InstallmentPlan> items) {
        InvoiceTotalsSummary totals = calculate(items);
        BigDecimal invoiceAmount = valueOrZero(invoice.getAmount());

        if (totals.totalAmount().compareTo(BigDecimal.ZERO) == 0 && invoiceAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal openAmount = invoiceAmount.subtract(totals.paidAmount());
            if (openAmount.compareTo(BigDecimal.ZERO) < 0) {
                openAmount = BigDecimal.ZERO;
            }
            return new InvoiceTotalsSummary(invoiceAmount, totals.paidAmount(), openAmount);
        }

        return totals;
    }

    /**
     * Identifica itens de pagamento para que eles abatam a fatura em vez de aumentar o total comprado.
     */
    public boolean isPaymentItem(InstallmentPlan item) {
        String name = item.getName() != null ? item.getName() : "";
        return name.startsWith("Pagamento Recebido");
    }

    /**
     * Normaliza valores monetários nulos para cálculos seguros.
     */
    public BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
