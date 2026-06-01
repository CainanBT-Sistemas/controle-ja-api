package com.cainanbt.softwares.controleja.workers;

import com.cainanbt.softwares.controleja.entities.RecurrenceRule;
import com.cainanbt.softwares.controleja.services.RecurrenceRuleService;
import com.cainanbt.softwares.controleja.services.TransactionService;
import com.cainanbt.softwares.controleja.utils.DateUtils;
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
    private final RecurrenceRuleService recurrenceRuleService;
    private final TransactionService transactionService;

    /**
     * Projeta lancamentos futuros para regras ativas, mantendo cada erro isolado por regra.
     */
    public void processProjections() {
        log.info("Iniciando motor de projecao de recorrencias");

        List<RecurrenceRule> activeRules = recurrenceRuleService.findAllActiveRules();
        LocalDate projectionLimit = LocalDate.now(DateUtils.zoneId).plusYears(1);

        int processed = 0;
        for (RecurrenceRule rule : activeRules) {
            try {
                transactionService.generateProjectionsForRule(rule, projectionLimit);
                processed++;
            } catch (Exception e) {
                log.error("Erro ao projetar recorrencia ruleId={}", rule.getId(), e);
            }
        }

        log.info("Motor de projecao de recorrencias concluido. rulesProcessed={}", processed);
    }
}
