package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
    List<RecurrenceRule> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, RuleStatus status);

    @EntityGraph(attributePaths = {"account", "targetAccount", "category", "user"})
    List<RecurrenceRule> findByStatusAndDeletedAtIsNull(RuleStatus status);
}