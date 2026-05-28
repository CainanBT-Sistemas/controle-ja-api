package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByEmailIgnoreCaseAndId(String email, UUID id);

    Optional<Users> findByEmailIgnoreCase(String email);

    // --- QUERIES PARA O RESET DE CONTA (HARD DELETE) ---

    @Modifying
    @Query(value = "DELETE FROM installment_plan WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteInstallmentsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM invoicess WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteInvoicesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM transactions WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteTransactionsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM credit_cards WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteCreditCardsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM vehicles WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteVehiclesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM category WHERE sub_category_id IS NOT NULL AND user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteSubCategoriesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM category WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteCategoriesByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM accounts WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteAccountsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM vehicle_logs WHERE user_id = CAST(:userId AS uuid)", nativeQuery = true)
    void deleteVehicleLogsByUserId(@Param("userId") UUID userId);
}
