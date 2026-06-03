package com.cainanbt.softwares.controleja.services.dashboard;

import com.cainanbt.softwares.controleja.dtos.dashboard.DashboardAlertDTO;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agrupa alertas do dashboard entre pendentes e vencidos.
 */
@Getter
public class DashboardAlertBuckets {
    private final List<DashboardAlertDTO> pending = new ArrayList<>();
    private final List<DashboardAlertDTO> overdue = new ArrayList<>();

    /**
     * Adiciona o alerta na lista correta comparando a data de vencimento com o inicio do dia atual.
     */
    public void add(DashboardAlertDTO alert, long todayEpoch) {
        if (alert.getDueDate() != null && alert.getDueDate() < todayEpoch) {
            overdue.add(alert);
            return;
        }
        pending.add(alert);
    }

    /**
     * Soma todos os valores dos alertas pendentes e vencidos do agrupamento.
     */
    public BigDecimal totalAmount() {
        return sum(pending).add(sum(overdue));
    }

    /**
     * Soma valores nulos como zero para evitar quebra em dados antigos.
     */
    private BigDecimal sum(List<DashboardAlertDTO> alerts) {
        return alerts.stream()
                .map(DashboardAlertDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
