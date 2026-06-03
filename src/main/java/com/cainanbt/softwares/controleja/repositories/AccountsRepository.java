package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountsRepository extends JpaRepository<Accounts, UUID> {
    /**
     * Lista as contas financeiras do usuário que aparecem nas telas de saldo e lançamento.
     */
    @Query("SELECT a FROM Accounts a WHERE a.user.id = :userId AND a.deletedAt IS NULL AND a.type <> com.cainanbt.softwares.controleja.enums.AccountType.CREDIT_CARD")
    List<Accounts> findByUserId(UUID userId);

    /**
     * Busca uma conta ativa pelo ID, respeitando soft delete.
     */
    @Query("SELECT a FROM Accounts a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<Accounts> findByIdAndNotDeleted(UUID id);

    /**
     * Localiza duplicidade de nome e tipo dentro das contas ativas do mesmo usuário.
     */
    @Query("SELECT a FROM Accounts a WHERE a.user.id = :userId AND a.name = :name AND a.type = :type AND a.deletedAt IS NULL")
    Optional<Accounts> findByUserIdAndNameAndType(UUID userId, String name, AccountType type);

    /**
     * Soma o saldo disponível do usuário usando a flag de participação no cálculo financeiro.
     */
    @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM Accounts a WHERE a.user.id = :userId AND a.deletedAt IS NULL AND a.calculateBalance = true AND a.type <> com.cainanbt.softwares.controleja.enums.AccountType.CREDIT_CARD")
    java.math.BigDecimal getAvailableBalanceByUserId(UUID userId);
}
