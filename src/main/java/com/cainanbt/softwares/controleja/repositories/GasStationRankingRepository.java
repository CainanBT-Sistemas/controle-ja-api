package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.enums.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GasStationRankingRepository extends JpaRepository<GasStationRanking, UUID> {
    /**
     * Busca ranking acumulado por posto e combustível.
     */
    Optional<GasStationRanking> findByGasStationAndFuelType(GasStation gasStation, FuelType fuelType);

    /**
     * Lista rankings dos postos pertencentes ao usuário ordenados por pontuação.
     */
    @Query("SELECT r FROM GasStationRanking r WHERE r.gasStation.user.id = :userId ORDER BY r.score DESC")
    List<GasStationRanking> findRankingsByUserId(@Param("userId") UUID userId);
}
