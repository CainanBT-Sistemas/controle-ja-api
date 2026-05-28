package com.cainanbt.softwares.controleja.dtos.dashboard;

import com.cainanbt.softwares.controleja.enums.FuelType;
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
    private BigDecimal estimatedNextRefuelCost; // Previsão de gasto mensal com abastecimento
    private BigDecimal estimatedNextCost; // Previsão mensal total do veículo com base no perfil de gastos

    // Dados do último abastecimento no período consultado
    private BigDecimal lastRefuelAmount;
    private BigDecimal lastFuelPricePerLiter;
    private Double lastRefuelDistanceKm;
    private Double lastRefuelKml;
    private FuelType lastRefuelFuelType;
}
