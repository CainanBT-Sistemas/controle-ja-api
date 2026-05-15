package com.cainanbt.softwares.controleja.dtos.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VehicleDashboardDTO {
    private BigDecimal monthlyCost;
    private BigDecimal yearlyCost;
    private BigDecimal costPerKm;

    private Double currentAvgKml; // A média atual (Gasolina ou Etanol)

    // Dados para o Alerta de Previsão
    private Double remainingKms;
    private Long estimatedNextRefuelDate; // Epoch de quando o tanque vai pedir reserva
    private BigDecimal estimatedNextRefuelCost; // Quanto vai custar para encher com base no último preço pago
}