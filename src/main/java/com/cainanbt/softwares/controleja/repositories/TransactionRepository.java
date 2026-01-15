package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, UUID> {
    List<Transactions> findByUserIdOrderByDateDesc(UUID userId);

    // Busca por intervalo de datas (Útil para filtro de Mês)
    List<Transactions> findByUserIdAndDateBetweenOrderByDateDesc(UUID userId, Long startDate, Long endDate);
}
