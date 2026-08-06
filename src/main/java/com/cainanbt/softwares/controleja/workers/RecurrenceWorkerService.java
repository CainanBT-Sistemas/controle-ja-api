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
        List<RecurrenceRule> activeRules = recurrenceRuleService.findAllActiveRules();
        LocalDate projectionLimit = LocalDate.now(DateUtils.zoneId).plusYears(1);

        for (RecurrenceRule rule : activeRules) {
            try {
                transactionService.generateProjectionsForRule(rule, projectionLimit);
            } catch (Exception e) {
                log.error("worker_error category=recurrence_projection exception={}", e.getClass().getSimpleName(), e);
            }
        }
    }
}
