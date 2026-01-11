package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmailIgnoreCaseAndId(String email, UUID id);

    Optional<Users> findByEmailIgnoreCase(String email);
}
