package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Invoices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoicesRepository extends JpaRepository<Invoices, UUID> {
    Optional<Invoices> findByCreditCardIdAndMonthAndYear(UUID creditCardId, Integer month, Integer year);
}
