package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.GasStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GasStationRepository extends JpaRepository<GasStation, UUID> {
    /**
     * Lista postos ativos pertencentes ao usuário.
     */
    List<GasStation> findByUserIdAndDeletedAtIsNull(UUID userId);

    /**
     * Busca posto ativo por id ignorando registros removidos logicamente.
     */
    Optional<GasStation> findByIdAndDeletedAtIsNull(UUID id);
}
