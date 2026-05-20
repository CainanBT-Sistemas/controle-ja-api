package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.GasStationRankingResponseDTO;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.entities.Transactions;
import com.cainanbt.softwares.controleja.repositories.GasStationRankingRepository;
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
    private final GasStationRankingRepository repository;

    @Override
    @Transactional
    public void updateRanking(Transactions tx) {
        // Só calcula se for um abastecimento válido
        if (tx.getGasStation() == null || tx.getLiters() == null || tx.getLiters() <= 0 || tx.getEfficiency() == null) {
            return;
        }

        GasStationRanking ranking = repository.findByGasStationAndFuelType(tx.getGasStation(), tx.getFuelType())
                .orElseGet(() -> GasStationRanking.builder()
                        .id(ID.generate())
                        .gasStation(tx.getGasStation())
                        .fuelType(tx.getFuelType())
                        .totalLiters(0.0)
                        .totalDistance(0.0)
                        .refuelCount(0)
                        .avgKml(0.0)
                        .avgCostPerKm(BigDecimal.ZERO)
                        .build());

        // Atualiza Acumulados
        ranking.setRefuelCount(ranking.getRefuelCount() + 1);
        ranking.setTotalLiters(ranking.getTotalLiters() + tx.getLiters());

        // Distância que este combustível rendeu = Km/L (Eficiência) * Litros
        double currentDistance = tx.getEfficiency() * tx.getLiters();
        ranking.setTotalDistance(ranking.getTotalDistance() + currentDistance);

        // Calcula Novas Médias
        ranking.setAvgKml(ranking.getTotalDistance() / ranking.getTotalLiters());

        BigDecimal pricePerLiter = tx.getAmount().divide(BigDecimal.valueOf(tx.getLiters()), 2, RoundingMode.HALF_UP);
        ranking.setLastPricePerLiter(pricePerLiter);

        // Custo por KM = Preço por Litro / Média Km/L
        BigDecimal costPerKm = pricePerLiter.divide(BigDecimal.valueOf(ranking.getAvgKml()), 4, RoundingMode.HALF_UP);
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
}
