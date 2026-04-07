package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurrenceRuleService {
    RecurrenceRule save(RecurrenceRule rule);

    Optional<RecurrenceRule> findById(UUID id);

    RecurrenceRule findByIdOrThrow(UUID id);

    List<RecurrenceRule> findActiveRulesByUser(UUID userId);
}
