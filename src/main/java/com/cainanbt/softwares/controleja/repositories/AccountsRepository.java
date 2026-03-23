package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountsRepository extends JpaRepository<Accounts, UUID> {
    @Query("SELECT a FROM Accounts a WHERE a.user.id = :userId AND a.deletedAt IS NULL")
    List<Accounts> findByUserId(UUID userId);
    
    @Query("SELECT a FROM Accounts a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<Accounts> findByIdAndNotDeleted(UUID id);
}