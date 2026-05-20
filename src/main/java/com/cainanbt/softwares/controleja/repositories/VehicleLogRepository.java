package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.VehicleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleLogRepository extends JpaRepository<VehicleLog, UUID> {
    List<VehicleLog> findByVehicleIdOrderByDateDesc(UUID vehicleId);

    Optional<VehicleLog> findFirstByVehicleIdOrderByDateAsc(UUID vehicleId);

    Optional<VehicleLog> findFirstByVehicleIdAndDateLessThanEqualOrderByDateAsc(UUID vehicleId, Long date);

    List<VehicleLog> findByVehicleIdAndDateBetweenOrderByDateDesc(UUID vehicleId, Long start, Long end);

    List<VehicleLog> findByVehicleIdAndDateBetweenOrderByDateAsc(UUID vehicleId, Long start, Long end);

    Optional<VehicleLog> findFirstByVehicleIdAndDateLessThanEqualOrderByDateDesc(UUID vehicleId, Long date);
}
