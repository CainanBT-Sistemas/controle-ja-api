package com.cainanbt.softwares.controleja.services.invoices;

import java.math.BigDecimal;

/**
 * Representa os valores consolidados de uma fatura após considerar compras, estornos e pagamentos.
 */
public record InvoiceTotalsSummary(BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal openAmount) {
}
