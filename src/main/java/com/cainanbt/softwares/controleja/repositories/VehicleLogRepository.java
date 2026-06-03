package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.VehicleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleLogRepository extends JpaRepository<VehicleLog, UUID> {
    /**
     * Busca leituras do veículo da mais recente para a mais antiga.
     */
    List<VehicleLog> findByVehicleIdOrderByDateDesc(UUID vehicleId);

    /**
     * Busca uma leitura específica garantindo que pertence ao usuário autenticado.
     */
    Optional<VehicleLog> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Busca a primeira leitura registrada do veículo.
     */
    Optional<VehicleLog> findFirstByVehicleIdOrderByDateAsc(UUID vehicleId);

    /**
     * Busca a primeira leitura registrada usando data e criação como critério determinístico.
     */
    Optional<VehicleLog> findFirstByVehicleIdOrderByDateAscCreatedAtAsc(UUID vehicleId);

    /**
     * Busca a última leitura registrada usando data e criação como critério determinístico.
     */
    Optional<VehicleLog> findFirstByVehicleIdOrderByDateDescCreatedAtDesc(UUID vehicleId);

    /**
     * Busca a primeira leitura registrada até uma data limite.
     */
    Optional<VehicleLog> findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(UUID vehicleId, Long date);

    /**
     * Busca leituras do veículo em período, da mais recente para a mais antiga.
     */
    List<VehicleLog> findByVehicleIdAndDateBetweenOrderByDateDesc(UUID vehicleId, Long start, Long end);

    /**
     * Busca leituras do veículo em período, da mais antiga para a mais recente.
     */
    List<VehicleLog> findByVehicleIdAndDateBetweenOrderByDateAsc(UUID vehicleId, Long start, Long end);

    /**
     * Busca a última leitura registrada até uma data limite.
     */
    Optional<VehicleLog> findFirstByVehicleIdAndDateLessThanEqualOrderByDateDesc(UUID vehicleId, Long date);

    /**
     * Busca a maior leitura de odômetro já registrada no diário de bordo.
     */
    @Query("SELECT MAX(l.odometerReading) FROM VehicleLog l WHERE l.vehicle.id = :vehicleId " +
            "AND l.odometerReading IS NOT NULL AND l.odometerReading > 0")
    BigDecimal findMaxOdometerReadingByVehicleId(@Param("vehicleId") UUID vehicleId);
}
