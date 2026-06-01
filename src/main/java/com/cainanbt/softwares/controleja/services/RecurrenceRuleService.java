package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurrenceRuleService {

    /**
     * Persiste uma regra de recorrencia nova ou alterada.
     */
    RecurrenceRule save(RecurrenceRule rule);

    /**
     * Busca uma regra pelo identificador sem disparar excecao.
     */
    Optional<RecurrenceRule> findById(UUID id);

    /**
     * Busca uma regra pelo identificador e falha com contrato 404 quando nao existir.
     */
    RecurrenceRule findByIdOrThrow(UUID id);

    /**
     * Lista regras ativas de um usuario especifico.
     */
    List<RecurrenceRule> findActiveRulesByUser(UUID userId);

    /**
     * Lista todas as regras ativas para processamento agendado de projecoes.
     */
    List<RecurrenceRule> findAllActiveRules();
}
