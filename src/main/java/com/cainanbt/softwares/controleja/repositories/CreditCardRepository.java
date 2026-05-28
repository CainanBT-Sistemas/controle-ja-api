package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    long countByUserId(UUID userId);

    @Query("SELECT c FROM CreditCard c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    List<CreditCard> findByUserId(UUID userId);

    Optional<CreditCard> findByAccountsId(UUID accountId);
    
    @Query("SELECT c FROM CreditCard c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CreditCard> findByIdAndNotDeleted(UUID id);
}