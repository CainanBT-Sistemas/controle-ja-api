package com.cainanbt.softwares.controleja.utils;

import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;

import java.math.BigDecimal;

/**
 * Centraliza as regras de validade para leituras de odometro.
 */
public final class OdometerValidator {
    private static final BigDecimal MAX_REASONABLE_ODOMETER = new BigDecimal("2000000");
    private static final BigDecimal MAX_ODOMETER_JUMP_WITHOUT_CONFIRMATION = new BigDecimal("20000");

    private OdometerValidator() {
    }

    /**
     * Valida se a leitura informada representa um KM absoluto plausivel.
     */
    public static void validateValue(BigDecimal odometer) {
        if (odometer == null) {
            return;
        }
        if (odometer.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Odômetro deve ser maior que zero.");
        }
        if (odometer.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Odômetro deve ser informado como KM absoluto, sem casas decimais.");
        }
        if (odometer.compareTo(MAX_REASONABLE_ODOMETER) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Odômetro informado está acima do limite plausível.");
        }
    }

    /**
     * Bloqueia saltos muito grandes para evitar erros de digitacao em lancamentos.
     */
    public static void validateJump(BigDecimal referenceOdometer, BigDecimal newOdometer) {
        if (referenceOdometer == null || newOdometer == null) {
            return;
        }
        BigDecimal jump = newOdometer.subtract(referenceOdometer);
        if (jump.compareTo(MAX_ODOMETER_JUMP_WITHOUT_CONFIRMATION) > 0) {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Salto de odômetro acima do limite permitido sem confirmação.");
        }
    }
}
