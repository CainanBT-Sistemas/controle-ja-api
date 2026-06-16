package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.GasStation;
import com.cainanbt.softwares.controleja.entities.GasStationRanking;
import com.cainanbt.softwares.controleja.enums.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Remove os acumuladores do usuário antes de reconstruir o ranking com os abastecimentos ativos.
     */
    @Modifying
    @Query(value = """
            DELETE FROM gas_station_rankings
            WHERE gas_station_id IN (
                SELECT id
                FROM gas_stations
                WHERE user_id = :userId
            )
            """, nativeQuery = true)
    void deleteByUserId(@Param("userId") UUID userId);
}
