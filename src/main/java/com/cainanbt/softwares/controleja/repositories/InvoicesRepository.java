package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Invoices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoicesRepository extends JpaRepository<Invoices, UUID> {
    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);

    @Query("SELECT i FROM Invoices i WHERE i.user.id = :userId AND i.expirationDate BETWEEN :start AND :end AND i.deletedAt IS NULL")
    List<Invoices> findByUserAndDateBetween(@Param("userId") UUID userId, @Param("start") Long start, @Param("end") Long end);
}
