package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.RecurrenceRuleRepository;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cainanbt.softwares.controleja.utils.ConstsMessages.RECURRENCE_RULE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecurrenceRuleServiceImpl implements RecurrenceRuleService {

    private final RecurrenceRuleRepository repository;

    @Override
    public RecurrenceRule save(RecurrenceRule rule) {
        return repository.save(rule);
    }

    @Override
    public Optional<RecurrenceRule> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public RecurrenceRule findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, RECURRENCE_RULE_NOT_FOUND));
    }

    @Override
    public List<RecurrenceRule> findActiveRulesByUser(UUID userId) {
        return repository.findByUserIdAndStatusAndDeletedAtIsNull(userId, RuleStatus.ACTIVE);
    }
}