package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class GasStationRankingResponseDTO {
    private UUID id;
    private String gasStationName;
    private String fuelType;
    private Double totalLiters;
    private Integer refuelCount;
    private Double avgKml;
    private BigDecimal avgCostPerKm;
    private BigDecimal lastPricePerLiter;
    private Double score;

    public static GasStationRankingResponseDTO toDTO(GasStationRanking entity) {
        return GasStationRankingResponseDTO.builder()
                .id(entity.getId())
                .gasStationName(entity.getGasStation().getName())
                .fuelType(entity.getFuelType().name())
                .totalLiters(entity.getTotalLiters())
                .refuelCount(entity.getRefuelCount())
                .avgKml(entity.getAvgKml())
                .avgCostPerKm(entity.getAvgCostPerKm())
                .lastPricePerLiter(entity.getLastPricePerLiter())
                .score(entity.getScore())
                .build();
    }
}
