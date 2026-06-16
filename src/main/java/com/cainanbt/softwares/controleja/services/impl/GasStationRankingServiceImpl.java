package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.repositories.GasStationRankingRepository;
import com.cainanbt.softwares.controleja.repositories.TransactionRepository;
import com.cainanbt.softwares.controleja.services.GasStationRankingService;
import com.cainanbt.softwares.controleja.services.gasstations.GasStationRankingCalculator;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GasStationRankingServiceImpl implements GasStationRankingService {
    private final GasStationRankingCalculator rankingCalculator = new GasStationRankingCalculator();

    private final GasStationRankingRepository repository;
    private final TransactionRepository transactionRepository;

    /**
     * Atualiza o ranking quando a transação representa um abastecimento com eficiência confiável.
     */
    @Override
    @Transactional
    public void updateRanking(Transactions tx) {
        if (!isUsableRefuel(tx)) {
            log.debug("Gas station ranking ignored for transaction without usable refuel data: transactionId={}",
                    tx != null ? tx.getId() : null);
            return;
        }

        DrivingPredominance predominance = resolveDrivingPredominance(tx);
        double currentDistance = tx.getEfficiency() * tx.getLiters();
        double adjustedEfficiency = rankingCalculator.normalizeEfficiency(tx.getEfficiency(), predominance);
        double adjustedDistance = adjustedEfficiency * tx.getLiters();

        GasStationRanking ranking = resolveRanking(tx);
        applyAccumulatedRefuel(ranking, tx, currentDistance, adjustedDistance, predominance);
        rankingCalculator.recalculate(ranking, tx.getAmount(), tx.getLiters());

        ranking.setUpdatedAt(DateUtils.getEpochNow());
        repository.save(ranking);
        log.info("Gas station ranking updated: stationId={}, fuelType={}, refuels={}",
                tx.getGasStation().getId(), tx.getFuelType(), ranking.getRefuelCount());
    }

    /**
     * Reconstrói os acumuladores a partir dos abastecimentos ativos para eliminar resíduos de edições e exclusões.
     */
    @Override
    @Transactional
    public void rebuildRankings(UUID userId) {
        repository.deleteByUserId(userId);

        Map<RankingKey, GasStationRanking> rankings = new LinkedHashMap<>();
        for (Transactions tx : transactionRepository.findValidRefuelsForRankingByUserId(userId)) {
            if (!isUsableRefuel(tx)) {
                continue;
            }

            DrivingPredominance predominance = resolveDrivingPredominance(tx);
            double currentDistance = tx.getEfficiency() * tx.getLiters();
            double adjustedEfficiency = rankingCalculator.normalizeEfficiency(tx.getEfficiency(), predominance);
            double adjustedDistance = adjustedEfficiency * tx.getLiters();
            RankingKey key = new RankingKey(tx.getGasStation().getId(), tx.getFuelType());
            GasStationRanking ranking = rankings.computeIfAbsent(key, ignored -> newRanking(tx));

            applyAccumulatedRefuel(ranking, tx, currentDistance, adjustedDistance, predominance);
            rankingCalculator.recalculate(ranking, tx.getAmount(), tx.getLiters());
            ranking.setUpdatedAt(DateUtils.getEpochNow());
        }

        if (!rankings.isEmpty()) {
            repository.saveAll(rankings.values());
        }
        log.info("Gas station rankings rebuilt: userId={}, rankings={}", userId, rankings.size());
    }

    /**
     * Lista rankings dos postos do usuário autenticado ordenados por score.
     */
    @Override
    public List<GasStationRankingResponseDTO> getMyRankings() {
        return repository.findRankingsByUserId(SecurityContextUtils.getCurrentUser().getId())
                .stream()
                .map(GasStationRankingResponseDTO::toDTO)
                .toList();
    }

    /**
     * Confirma se a transação possui dados suficientes para ranking confiável.
     */
    private boolean isUsableRefuel(Transactions tx) {
        return tx != null
                && tx.getGasStation() != null
                && tx.getFuelType() != null
                && tx.getLiters() != null
                && tx.getLiters() > 0
                && tx.getEfficiency() != null
                && tx.getEfficiency() > 0
                && tx.getAmount() != null
                && tx.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Busca ranking existente ou cria um acumulador inicial para o posto e combustível.
     */
    private GasStationRanking resolveRanking(Transactions tx) {
        return repository.findByGasStationAndFuelType(tx.getGasStation(), tx.getFuelType())
                .orElseGet(() -> newRanking(tx));
    }

    /**
     * Cria um acumulador vazio para um par de posto e combustível.
     */
    private GasStationRanking newRanking(Transactions tx) {
        return GasStationRanking.builder()
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
                .build();
    }

    /**
     * Soma o novo abastecimento nos acumuladores do ranking.
     */
    private void applyAccumulatedRefuel(
            GasStationRanking ranking,
            Transactions tx,
            double currentDistance,
            double adjustedDistance,
            DrivingPredominance predominance) {
        ranking.setRefuelCount(nullToZero(ranking.getRefuelCount()) + 1);
        ranking.setTotalLiters(nullToZero(ranking.getTotalLiters()) + tx.getLiters());
        ranking.setTotalDistance(nullToZero(ranking.getTotalDistance()) + currentDistance);
        ranking.setTotalAdjustedDistance(nullToZero(ranking.getTotalAdjustedDistance()) + adjustedDistance);
        ranking.setTotalAmount(nullToZero(ranking.getTotalAmount()).add(tx.getAmount()));
        incrementPredominanceCounter(ranking, predominance);
    }

    /**
     * Usa a predominância registrada no próprio abastecimento.
     */
    private DrivingPredominance resolveDrivingPredominance(Transactions tx) {
        return tx.getDrivingPredominance();
    }

    /**
     * Incrementa o contador por tipo de predominância usado no abastecimento.
     */
    private void incrementPredominanceCounter(GasStationRanking ranking, DrivingPredominance predominance) {
        if (predominance == DrivingPredominance.CITY) {
            ranking.setCityRefuelCount(nullToZero(ranking.getCityRefuelCount()) + 1);
        } else if (predominance == DrivingPredominance.ROAD) {
            ranking.setRoadRefuelCount(nullToZero(ranking.getRoadRefuelCount()) + 1);
        } else {
            ranking.setUnknownRefuelCount(nullToZero(ranking.getUnknownRefuelCount()) + 1);
        }
    }

    /**
     * Trata inteiros nulos como zero para acumuladores legados.
     */
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Trata doubles nulos como zero para acumuladores legados.
     */
    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * Trata valores monetários nulos como zero para acumuladores legados.
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record RankingKey(UUID gasStationId, com.cainanbt.softwares.controleja.enums.FuelType fuelType) {
    }
}
