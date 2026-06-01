package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    /**
     * Busca veículos ativos do usuário.
     */
    List<Vehicle> findByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Busca veículo ativo por id, ignorando registros removidos logicamente.
     */
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<Vehicle> findByIdAndNotDeleted(UUID id);
}
