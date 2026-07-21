package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {

    @EntityGraph(attributePaths = {"account", "targetAccount", "category", "user"})
    List<RecurrenceRule> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, RuleStatus status);

    @EntityGraph(attributePaths = {"account", "targetAccount", "category", "user"})
    List<RecurrenceRule> findByStatusAndDeletedAtIsNull(RuleStatus status);

    /**
     * Verifica se a conta participa de alguma regra recorrente ativa.
     */
    @Query("SELECT COUNT(r) > 0 FROM RecurrenceRule r WHERE r.user.id = :userId AND r.status = com.cainanbt.softwares.controleja.enums.RuleStatus.ACTIVE AND r.deletedAt IS NULL AND (r.account.id = :accountId OR (r.targetAccount IS NOT NULL AND r.targetAccount.id = :accountId))")
    boolean existsActiveByAccountIdOrTargetAccountIdAndUserId(@Param("accountId") UUID accountId, @Param("userId") UUID userId);
}
