package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.repositories.RecurrenceRuleRepository;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cainanbt.softwares.controleja.utils.ConstsMessages.RECURRENCE_RULE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class RecurrenceRuleServiceImpl implements RecurrenceRuleService {

    private final RecurrenceRuleRepository repository;

    /**
     * Salva a regra de recorrencia usada para gerar lancamentos futuros.
     */
    @Override
    @Transactional
    public RecurrenceRule save(RecurrenceRule rule) {
        return repository.save(rule);
    }

    /**
     * Busca uma regra por id sem impor regra de negocio no chamador.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RecurrenceRule> findById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Busca uma regra por id e devolve erro padronizado quando ela nao existe.
     */
    @Override
    @Transactional(readOnly = true)
    public RecurrenceRule findByIdOrThrow(UUID id) {
        return findById(id).orElseThrow(() ->
                new EntityNotFoundException(ConstsMessages.ERROR_TITLE, RECURRENCE_RULE_NOT_FOUND));
    }

    /**
     * Carrega regras ativas de um usuario com os relacionamentos necessarios para projecao.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecurrenceRule> findActiveRulesByUser(UUID userId) {
        return repository.findByUserIdAndStatusAndDeletedAtIsNull(userId, RuleStatus.ACTIVE);
    }

    /**
     * Carrega todas as regras ativas usadas pelo worker de recorrencias.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecurrenceRule> findAllActiveRules() {
        return repository.findByStatusAndDeletedAtIsNull(RuleStatus.ACTIVE);
    }
}
