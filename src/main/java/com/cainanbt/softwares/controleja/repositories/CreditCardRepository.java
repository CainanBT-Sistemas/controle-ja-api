package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    long countByUserId(UUID userId);
}