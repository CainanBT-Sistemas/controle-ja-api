package com.cainanbt.softwares.controleja.services.retention;

import com.cainanbt.softwares.controleja.repositories.SoftDeletedDataPurgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SoftDeletedDataPurgeService {
    private final SoftDeletedDataPurgeRepository repository;
    private final TransactionTemplate transactionTemplate;

    public PurgeRunResult purge(long cutoff, boolean dryRun, int batchSize, int maxBatches) {
        validateLimits(batchSize, maxBatches);
        Map<String, Long> totals = emptyCounts();
        EnumMap<PurgeTable, Set<UUID>> virtuallyRemoved = emptyIdSets();
        EnumMap<PurgeTable, Set<UUID>> selectionExcluded = emptyIdSets();
        int completedBatches = 0;

        while (completedBatches < maxBatches) {
            BatchExecution execution = transactionTemplate.execute(status -> {
                PurgePlan currentPlan = repository.planBatch(
                        cutoff,
                        batchSize,
                        virtuallyRemoved,
                        selectionExcluded
                );
                Map<String, Long> counts = dryRun || currentPlan.isEmpty()
                        ? currentPlan.projectedCounts()
                        : executePlan(currentPlan);
                return new BatchExecution(currentPlan, counts);
            });
            if (execution == null || execution.plan().consideredNothing()) {
                break;
            }

            PurgePlan plan = execution.plan();
            excludeConsidered(plan, selectionExcluded);
            if (plan.isEmpty()) {
                continue;
            }

            if (dryRun) {
                markVirtuallyRemoved(plan, virtuallyRemoved);
            }
            mergeCounts(totals, execution.counts());
            completedBatches++;
        }

        return new PurgeRunResult(dryRun, completedBatches, totals);
    }

    private Map<String, Long> executePlan(PurgePlan plan) {
        Map<String, Long> counts = emptyCounts();

        counts.put("installment_plan", (long) repository.delete(
                PurgeTable.INSTALLMENT_PLAN,
                plan.ids(PurgeTable.INSTALLMENT_PLAN)
        ));
        repository.clearEligibleInvoiceTransactionCycles(
                plan.ids(PurgeTable.TRANSACTIONS),
                plan.ids(PurgeTable.INVOICES)
        );
        counts.put("transactions", (long) repository.delete(
                PurgeTable.TRANSACTIONS,
                plan.ids(PurgeTable.TRANSACTIONS)
        ));
        counts.put("invoicess", (long) repository.delete(
                PurgeTable.INVOICES,
                plan.ids(PurgeTable.INVOICES)
        ));
        counts.put("gas_station_rankings", (long) repository.deleteGasStationRankings(
                plan.ids(PurgeTable.GAS_STATIONS)
        ));
        counts.put("gas_stations", (long) repository.delete(
                PurgeTable.GAS_STATIONS,
                plan.ids(PurgeTable.GAS_STATIONS)
        ));
        counts.put("recurrence_rules", (long) repository.delete(
                PurgeTable.RECURRENCE_RULES,
                plan.ids(PurgeTable.RECURRENCE_RULES)
        ));
        counts.put("credit_cards", (long) repository.delete(
                PurgeTable.CREDIT_CARDS,
                plan.ids(PurgeTable.CREDIT_CARDS)
        ));
        counts.put("vehicle_logs", (long) repository.deleteVehicleLogs(
                plan.ids(PurgeTable.VEHICLES)
        ));
        counts.put("vehicles", (long) repository.delete(
                PurgeTable.VEHICLES,
                plan.ids(PurgeTable.VEHICLES)
        ));
        counts.put("category", (long) repository.delete(
                PurgeTable.CATEGORIES,
                plan.ids(PurgeTable.CATEGORIES)
        ));
        counts.put("accounts", (long) repository.delete(
                PurgeTable.ACCOUNTS,
                plan.ids(PurgeTable.ACCOUNTS)
        ));
        return counts;
    }

    private void excludeConsidered(
            PurgePlan plan,
            Map<PurgeTable, Set<UUID>> selectionExcluded
    ) {
        plan.consideredIds().forEach((table, ids) -> selectionExcluded.get(table).addAll(ids));
    }

    private void markVirtuallyRemoved(
            PurgePlan plan,
            Map<PurgeTable, Set<UUID>> virtuallyRemoved
    ) {
        plan.allIds().forEach((table, ids) -> virtuallyRemoved.get(table).addAll(ids));
    }

    private EnumMap<PurgeTable, Set<UUID>> emptyIdSets() {
        EnumMap<PurgeTable, Set<UUID>> values = new EnumMap<>(PurgeTable.class);
        for (PurgeTable table : PurgeTable.values()) {
            values.put(table, new LinkedHashSet<>());
        }
        return values;
    }

    private Map<String, Long> emptyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PurgeTable table : PurgeTable.values()) {
            counts.put(table.tableName(), 0L);
        }
        counts.put("gas_station_rankings", 0L);
        counts.put("vehicle_logs", 0L);
        return counts;
    }

    private void mergeCounts(Map<String, Long> totals, Map<String, Long> batchCounts) {
        batchCounts.forEach((table, count) -> totals.merge(table, count, Long::sum));
    }

    private void validateLimits(int batchSize, int maxBatches) {
        if (batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException("Batch size and maximum batches must be positive");
        }
    }

    private record BatchExecution(PurgePlan plan, Map<String, Long> counts) {
    }
}
