package com.cainanbt.softwares.controleja.services.invoices;

import com.cainanbt.softwares.controleja.entities.CreditCard;
import com.cainanbt.softwares.controleja.entities.Invoices;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Concentra regras de calendário e status de faturas de cartão.
 */
@Component
public class InvoiceDateService {

    /**
     * Calcula o fechamento da fatura considerando dia de fechamento, melhor dia e ajuste para dia útil.
     */
    public LocalDate calculateCloseDate(CreditCard card, Integer month, Integer year) {
        LocalDate closeDate = safeDate(year, month, card.getCloseDay());
        if (card.getCloseDay() > card.getBestDay()) {
            closeDate = closeDate.minusMonths(1);
        }
        return nextBusinessDay(closeDate);
    }

    /**
     * Calcula o vencimento da fatura ajustando finais de semana para o próximo dia útil.
     */
    public LocalDate calculateExpirationDate(CreditCard card, Integer month, Integer year) {
        return nextBusinessDay(safeDate(year, month, card.getBestDay()));
    }

    /**
     * Calcula o fechamento da fatura anterior para descobrir se a fatura atual está aberta para lançamentos.
     */
    public LocalDate calculatePreviousCloseDate(CreditCard card, Integer month, Integer year) {
        LocalDate invoiceMonth = LocalDate.of(year, month, 1).minusMonths(1);
        return calculateCloseDate(card, invoiceMonth.getMonthValue(), invoiceMonth.getYear());
    }

    /**
     * Resolve o status operacional da fatura com base no calendário e no saldo em aberto.
     */
    public String calculateInvoiceStatus(CreditCard card, Boolean paid, BigDecimal openAmount, LocalDate closeDate, LocalDate expirationDate, Integer month, Integer year) {
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        LocalDate previousCloseDate = calculatePreviousCloseDate(card, month, year);

        if (!today.isBefore(previousCloseDate) && today.isBefore(closeDate)) {
            return "ABERTA";
        }

        if (today.isAfter(expirationDate)) {
            if (Boolean.TRUE.equals(paid) || openAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return "PAGA";
            }
            return "ATRASADA";
        }

        if (!today.isBefore(closeDate) && !today.isAfter(expirationDate)) {
            if (Boolean.TRUE.equals(paid) || openAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return "PAGA";
            }
            return "FECHADA";
        }

        return "FUTURA";
    }

    /**
     * Indica se o status bloqueia edição de itens da fatura.
     */
    public boolean isClosedOrPaid(String status) {
        return "PAGA".equals(status) || "ATRASADA".equals(status) || "FECHADA".equals(status);
    }

    /**
     * Indica se a fatura ainda está na janela aberta entre fechamento anterior e fechamento atual.
     */
    public boolean isInvoiceOpenWindow(Invoices invoice) {
        if (invoice.getCreditCard() == null || invoice.getMonth() == null || invoice.getYear() == null) {
            return false;
        }
        LocalDate today = LocalDate.now(DateUtils.zoneId);
        LocalDate closeDate = calculateCloseDate(invoice.getCreditCard(), invoice.getMonth(), invoice.getYear());
        LocalDate previousCloseDate = calculatePreviousCloseDate(invoice.getCreditCard(), invoice.getMonth(), invoice.getYear());
        return !today.isBefore(previousCloseDate) && today.isBefore(closeDate);
    }

    /**
     * Cria uma data válida mesmo quando o cartão fecha ou vence em dia inexistente no mês.
     */
    private LocalDate safeDate(Integer year, Integer month, int requestedDay) {
        try {
            int monthLength = LocalDate.of(year, month, 1).lengthOfMonth();
            return LocalDate.of(year, month, Math.min(requestedDay, monthLength));
        } catch (DateTimeException e) {
            return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    /**
     * Avança uma data até o próximo dia útil conhecido.
     */
    private LocalDate nextBusinessDay(LocalDate date) {
        LocalDate adjusted = date;
        while (isWeekend(adjusted) || isHoliday(adjusted)) {
            adjusted = adjusted.plusDays(1);
        }
        return adjusted;
    }

    /**
     * Identifica sábados e domingos.
     */
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Ponto único para plugar calendário de feriados quando o projeto tiver essa fonte.
     */
    private boolean isHoliday(LocalDate date) {
        return false;
    }
}
