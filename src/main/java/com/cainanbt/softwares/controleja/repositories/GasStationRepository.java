package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.GasStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GasStationRepository extends JpaRepository<GasStation, UUID> {
    List<GasStation> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<GasStation> findByIdAndDeletedAtIsNull(UUID id);
}