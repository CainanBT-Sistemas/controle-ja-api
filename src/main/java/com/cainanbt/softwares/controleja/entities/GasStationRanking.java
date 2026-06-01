package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.FuelType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "gas_station_rankings", indexes = {
        @Index(name = "idx_gas_rankings_station_fuel", columnList = "gas_station_id, fuelType"),
        @Index(name = "idx_gas_rankings_score", columnList = "score")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GasStationRanking {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gas_station_id")
    private GasStation gasStation;

    @Enumerated(EnumType.STRING)
    private FuelType fuelType;

    private Double totalLiters;
    private Double totalDistance;
    private Double totalAdjustedDistance;
    private BigDecimal totalAmount;
    private Integer refuelCount;
    private Integer cityRefuelCount;
    private Integer roadRefuelCount;
    private Integer unknownRefuelCount;
    private Double avgKml;
    private Double adjustedAvgKml;
    private BigDecimal avgCostPerKm;
    private BigDecimal lastPricePerLiter;
    private Double score; // De 0 a 10
    private Long updatedAt;
}
