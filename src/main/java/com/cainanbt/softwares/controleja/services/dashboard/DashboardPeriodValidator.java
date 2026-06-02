package com.cainanbt.softwares.controleja.services.dashboard;

import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;

/**
 * Centraliza validacoes do periodo usado nas consultas do dashboard.
 */
public class DashboardPeriodValidator {

    /**
     * Garante que o intervalo informado existe e esta em ordem cronologica.
     */
    public void validate(Long start, Long end) {
        if (start == null || end == null) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Informe start e end para consultar o dashboard.");
        }
        if (start > end) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "A data inicial não pode ser maior que a data final.");
        }
    }
}
