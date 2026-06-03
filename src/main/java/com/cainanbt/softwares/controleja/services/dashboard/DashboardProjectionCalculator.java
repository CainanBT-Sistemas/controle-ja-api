package com.cainanbt.softwares.controleja.services.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula os totais projetados exibidos no resumo completo do dashboard.
 */
public class DashboardProjectionCalculator {

    /**
     * Calcula a media mensal de despesas variaveis baseada nos ultimos tres meses.
     */
    public BigDecimal averageLastThreeMonths(BigDecimal totalLastThreeMonths) {
        return nullToZero(totalLastThreeMonths).divide(new BigDecimal(3), 2, RoundingMode.HALF_UP);
    }

    /**
     * Projeta o saldo considerando saldo atual, recebiveis, contas/faturas e variaveis medias.
     */
    public BigDecimal projectedBalance(
            BigDecimal availableBalance,
            BigDecimal projectedReceivables,
            BigDecimal projectedPayables,
            BigDecimal projectedVariables) {
        return nullToZero(availableBalance)
                .add(nullToZero(projectedReceivables))
                .subtract(nullToZero(projectedPayables))
                .subtract(nullToZero(projectedVariables));
    }

    /**
     * Trata valores nulos como zero para calculos financeiros do dashboard.
     */
    public BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
