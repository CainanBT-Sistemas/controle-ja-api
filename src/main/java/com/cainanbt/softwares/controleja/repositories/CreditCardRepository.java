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
    /**
     * Conta cartões ativos do usuário para aplicar limites do plano.
     */
    long countByUserId(UUID userId);

    /**
     * Lista cartões ativos do usuário autenticado.
     */
    @Query("SELECT c FROM CreditCard c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    List<CreditCard> findByUserId(UUID userId);

    /**
     * Localiza o cartão pela conta espelho usada em transações de fatura.
     */
    Optional<CreditCard> findByAccountsId(UUID accountId);

    /**
     * Busca cartão ativo por id ignorando registros removidos logicamente.
     */
    @Query("SELECT c FROM CreditCard c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CreditCard> findByIdAndNotDeleted(UUID id);

    /**
     * Verifica se a conta ainda esta vinculada a um cartao ativo.
     */
    @Query("SELECT COUNT(c) > 0 FROM CreditCard c WHERE c.accounts.id = :accountId AND c.user.id = :userId AND c.deletedAt IS NULL")
    boolean existsActiveByAccountIdAndUserId(UUID accountId, UUID userId);
}
