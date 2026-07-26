package com.cainanbt.softwares.controleja.dtos.dashboard;

import com.cainanbt.softwares.controleja.enums.FuelType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class VehicleDashboardDTO {
    private BigDecimal monthlyCost;
    private BigDecimal yearlyCost;
    private BigDecimal costPerKm;
    private Boolean costPerKmReliable;

    private Double currentAvgKml; // A média atual (Gasolina ou Etanol)

    // Dados para o Alerta de Previsão
    private Double remainingKms;
    private Long estimatedNextRefuelDate; // Epoch de quando o tanque vai pedir reserva
    private BigDecimal estimatedNextRefuelCost; // Previsão de gasto mensal com abastecimento
    private BigDecimal estimatedNextCost; // Previsão mensal total do veículo com base no perfil de gastos

    // Contrato novo de previsão, mantido junto dos campos legados para compatibilidade com o app atual.
    private BigDecimal nextMonthEstimatedCost;
    private String nextMonthEstimatedCostConfidence;
    private VehicleRefuelPredictionDTO nextRefuelPrediction;
    private List<VehicleFuturePredictionDTO> futurePredictions;

    // Dados do último abastecimento no período consultado
    private BigDecimal lastRefuelAmount;
    private BigDecimal lastFuelPricePerLiter;
    private Double lastRefuelDistanceKm;
    private Double lastRefuelKml;
    private FuelType lastRefuelFuelType;

    @Data
    @Builder
    public static class VehicleRefuelPredictionDTO {
        private Long estimatedDate;
        private BigDecimal estimatedCost;
        private Double estimatedLiters;
        private FuelType fuelType;
        private String confidence;
        private String basis;
    }

    @Data
    @Builder
    public static class VehicleFuturePredictionDTO {
        private String month;
        private BigDecimal estimatedCost;
        private Integer estimatedRefuels;
        private String confidence;
        private List<VehicleFuturePredictionItemDTO> items;
    }

    @Data
    @Builder
    public static class VehicleFuturePredictionItemDTO {
        private String type;
        private String description;
        private Long estimatedDate;
        private BigDecimal estimatedCost;
        private String confidence;
    }
}
