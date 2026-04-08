package com.cainanbt.softwares.controleja.workers;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.enums.RuleStatus;
import com.cainanbt.softwares.controleja.repositories.RecurrenceRuleRepository;
import com.cainanbt.softwares.controleja.services.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
@EnableScheduling
public class RecurrenceWorkerService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TransactionService transactionService;

    public void processProjections() {
        log.info("Iniciando Motor de Projeção de Recorrências...");

        // Busca todas as regras de todos os usuários que estão ativas
        List<RecurrenceRule> activeRules = recurrenceRuleRepository.findByStatusAndDeletedAtIsNull(RuleStatus.ACTIVE);

        // Nossa janela de visão: 1 ano para o futuro
        LocalDate projectionLimit = LocalDate.now().plusYears(1);

        int processed = 0;
        for (RecurrenceRule rule : activeRules) {
            try {
                // A inteligência ignora o que já foi criado e foca apenas nos meses faltantes
                transactionService.generateProjectionsForRule(rule, projectionLimit);
                processed++;
            } catch (Exception e) {
                log.error("Erro ao processar projeção para a regra ID: {}", rule.getId(), e);
            }
        }

        log.info("Motor de Projeção concluído. {} Regras validadas/projetadas com sucesso.", processed);
    }
}
