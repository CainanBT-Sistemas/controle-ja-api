package com.cainanbt.softwares.controleja.services.gasstations;

import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.enums.DrivingPredominance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula métricas normalizadas do ranking de postos.
 */
public class GasStationRankingCalculator {

    private static final double DEFAULT_CITY_FACTOR = 0.90;
    private static final double DEFAULT_ROAD_FACTOR = 1.12;

    /**
     * Normaliza o KM/L observado para comparar ciclos cidade/estrada em uma base mista.
     */
    public double normalizeEfficiency(Double observedKml, DrivingPredominance predominance) {
        if (predominance == DrivingPredominance.CITY) {
            return observedKml / DEFAULT_CITY_FACTOR;
        }
        if (predominance == DrivingPredominance.ROAD) {
            return observedKml / DEFAULT_ROAD_FACTOR;
        }
        return observedKml;
    }

    /**
     * Recalcula médias, custo por km, preço por litro e score do ranking.
     */
    public void recalculate(GasStationRanking ranking, BigDecimal transactionAmount, Double transactionLiters) {
        ranking.setAvgKml(ranking.getTotalDistance() / ranking.getTotalLiters());
        ranking.setAdjustedAvgKml(ranking.getTotalAdjustedDistance() / ranking.getTotalLiters());

        BigDecimal pricePerLiter = transactionAmount.divide(BigDecimal.valueOf(transactionLiters), 2, RoundingMode.HALF_UP);
        ranking.setLastPricePerLiter(pricePerLiter);

        BigDecimal costPerKm = ranking.getTotalAmount()
                .divide(BigDecimal.valueOf(ranking.getTotalAdjustedDistance()), 4, RoundingMode.HALF_UP);
        ranking.setAvgCostPerKm(costPerKm);
        ranking.setScore(calculateScore(costPerKm));
    }

    /**
     * Converte custo por quilômetro em nota de 0 a 10, quanto menor o custo maior a nota.
     */
    public Double calculateScore(BigDecimal costPerKm) {
        double score = 10.0 - (costPerKm.doubleValue() * 10);
        return Math.max(0.0, Math.min(10.0, score));
    }
}
