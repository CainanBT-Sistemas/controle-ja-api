package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmailIgnoreCaseAndId(String email, UUID id);

    Optional<Users> findByEmailIgnoreCase(String email);

    @Query(value = "SELECT * FROM users WHERE lower(email) = :email ORDER BY deleted_at NULLS FIRST, created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Users> findAnyByEmail(@Param("email") String normalizedEmail);

    @Query(value = "SELECT * FROM users WHERE lower(email) = :email ORDER BY deleted_at NULLS FIRST, created_at DESC LIMIT 1 FOR UPDATE", nativeQuery = true)
    Optional<Users> findAnyByEmailForUpdate(@Param("email") String normalizedEmail);
}
