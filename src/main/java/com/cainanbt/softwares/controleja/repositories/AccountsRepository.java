package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AccountsRepository extends JpaRepository<Accounts, UUID> {
    List<Accounts> findByUserId(UUID userId);
}