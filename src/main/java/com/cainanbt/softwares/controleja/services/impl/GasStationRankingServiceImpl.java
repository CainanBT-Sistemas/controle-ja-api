package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.entities.VehicleLog;
import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.repositories.GasStationRankingRepository;
import com.cainanbt.softwares.controleja.repositories.VehicleLogRepository;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GasStationRankingServiceImpl implements GasStationRankingService {
    private static final double DEFAULT_CITY_FACTOR = 0.90;
    private static final double DEFAULT_ROAD_FACTOR = 1.12;

    private final GasStationRankingRepository repository;
    private final VehicleLogRepository vehicleLogRepository;

    @Override
    @Transactional
    public void updateRanking(Transactions tx) {
        // Só calcula se for um abastecimento válido
        if (tx.getGasStation() == null || tx.getLiters() == null || tx.getLiters() <= 0
                || tx.getEfficiency() == null || tx.getEfficiency() <= 0
                || tx.getAmount() == null || tx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        DrivingPredominance predominance = resolveDrivingPredominance(tx);
        double currentDistance = tx.getEfficiency() * tx.getLiters();
        double adjustedEfficiency = normalizeEfficiency(tx.getEfficiency(), predominance);
        double adjustedDistance = adjustedEfficiency * tx.getLiters();

        GasStationRanking ranking = repository.findByGasStationAndFuelType(tx.getGasStation(), tx.getFuelType())
                .orElseGet(() -> GasStationRanking.builder()
                        .id(ID.generate())
                        .gasStation(tx.getGasStation())
                        .fuelType(tx.getFuelType())
                        .totalLiters(0.0)
                        .totalDistance(0.0)
                        .totalAdjustedDistance(0.0)
                        .totalAmount(BigDecimal.ZERO)
                        .refuelCount(0)
                        .cityRefuelCount(0)
                        .roadRefuelCount(0)
                        .unknownRefuelCount(0)
                        .avgKml(0.0)
                        .adjustedAvgKml(0.0)
                        .avgCostPerKm(BigDecimal.ZERO)
                        .build());

        // Atualiza Acumulados
        ranking.setRefuelCount(nullToZero(ranking.getRefuelCount()) + 1);
        ranking.setTotalLiters(nullToZero(ranking.getTotalLiters()) + tx.getLiters());
        ranking.setTotalDistance(nullToZero(ranking.getTotalDistance()) + currentDistance);
        ranking.setTotalAdjustedDistance(nullToZero(ranking.getTotalAdjustedDistance()) + adjustedDistance);
        ranking.setTotalAmount(nullToZero(ranking.getTotalAmount()).add(tx.getAmount()));
        incrementPredominanceCounter(ranking, predominance);

        // Calcula Novas Médias
        ranking.setAvgKml(ranking.getTotalDistance() / ranking.getTotalLiters());
        ranking.setAdjustedAvgKml(ranking.getTotalAdjustedDistance() / ranking.getTotalLiters());

        BigDecimal pricePerLiter = tx.getAmount().divide(BigDecimal.valueOf(tx.getLiters()), 2, RoundingMode.HALF_UP);
        ranking.setLastPricePerLiter(pricePerLiter);

        // Custo por KM ajustado = custo total / distância normalizada para um ciclo misto cidade/estrada.
        BigDecimal costPerKm = ranking.getTotalAmount().divide(BigDecimal.valueOf(ranking.getTotalAdjustedDistance()), 4, RoundingMode.HALF_UP);
        ranking.setAvgCostPerKm(costPerKm);

        // Algoritmo de Pontuação de 0 a 10 (Quanto menor o custo, maior a nota)
        ranking.setScore(calculateScore(costPerKm));

        ranking.setUpdatedAt(DateUtils.getEpochNow());
        repository.save(ranking);
    }

    @Override
    public List<GasStationRankingResponseDTO> getMyRankings() {
        return repository.findRankingsByUserId(SecurityContextUtils.getCurrentUser().getId())
                .stream()
                .map(GasStationRankingResponseDTO::toDTO)
                .collect(Collectors.toList());
    }

    private Double calculateScore(BigDecimal costPerKm) {
        // Exemplo: Se o custo for R$ 0,50/km a nota é 5. Se for R$ 0,30/km a nota é 7.
        // Base de cálculo: 10 - (custo * 10). O Math.max e min garante que não passa de 10 nem fica negativo.
        double score = 10.0 - (costPerKm.doubleValue() * 10);
        return Math.max(0.0, Math.min(10.0, score));
    }

    private DrivingPredominance resolveDrivingPredominance(Transactions tx) {
        if (tx.getDrivingPredominance() != null) {
            return tx.getDrivingPredominance();
        }
        if (tx.getVehicle() == null || tx.getDate() == null) {
            return null;
        }
        return vehicleLogRepository.findFirstByVehicleIdAndDateLessThanEqualOrderByDateDesc(tx.getVehicle().getId(), tx.getDate())
                .map(VehicleLog::getDrivingPredominance)
                .orElse(null);
    }

    private double normalizeEfficiency(Double observedKml, DrivingPredominance predominance) {
        if (predominance == DrivingPredominance.CITY) {
            return observedKml / DEFAULT_CITY_FACTOR;
        }
        if (predominance == DrivingPredominance.ROAD) {
            return observedKml / DEFAULT_ROAD_FACTOR;
        }
        return observedKml;
    }

    private void incrementPredominanceCounter(GasStationRanking ranking, DrivingPredominance predominance) {
        if (predominance == DrivingPredominance.CITY) {
            ranking.setCityRefuelCount(nullToZero(ranking.getCityRefuelCount()) + 1);
        } else if (predominance == DrivingPredominance.ROAD) {
            ranking.setRoadRefuelCount(nullToZero(ranking.getRoadRefuelCount()) + 1);
        } else {
            ranking.setUnknownRefuelCount(nullToZero(ranking.getUnknownRefuelCount()) + 1);
        }
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
