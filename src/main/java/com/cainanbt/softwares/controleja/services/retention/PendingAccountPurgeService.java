package com.cainanbt.softwares.controleja.services.retention;

import com.cainanbt.softwares.controleja.repositories.PendingAccountPurgeRepository;
import com.cainanbt.softwares.controleja.services.users.UserOperationalDataResetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingAccountPurgeService {
    private final PendingAccountPurgeRepository repository;
    private final UserOperationalDataResetter operationalDataResetter;
    private final TransactionTemplate transactionTemplate;

    public PendingAccountPurgeResult purge(
            long cutoff,
            boolean dryRun,
            int batchSize,
            int maxBatches
    ) {
        validateLimits(batchSize, maxBatches);
        return dryRun
                ? project(cutoff, batchSize, maxBatches)
                : execute(cutoff, batchSize, maxBatches);
    }

    private PendingAccountPurgeResult project(long cutoff, int batchSize, int maxBatches) {
        Set<UUID> projected = new LinkedHashSet<>();
        long tokenCount = 0;
        int batches = 0;

        while (batches < maxBatches) {
            List<UUID> candidates = repository.findEligibleForProjection(
                    cutoff,
                    batchSize,
                    projected
            );
            if (candidates.isEmpty()) {
                break;
            }
            projected.addAll(candidates);
            tokenCount += repository.countTokens(candidates);
            batches++;
        }

        return new PendingAccountPurgeResult(true, batches, projected.size(), tokenCount);
    }

    private PendingAccountPurgeResult execute(long cutoff, int batchSize, int maxBatches) {
        long users = 0;
        long tokens = 0;
        int batches = 0;

        while (batches < maxBatches) {
            BatchResult batch = transactionTemplate.execute(status -> executeBatch(cutoff, batchSize));
            if (batch == null || batch.users() == 0) {
                break;
            }
            users += batch.users();
            tokens += batch.tokens();
            batches++;
        }

        return new PendingAccountPurgeResult(false, batches, users, tokens);
    }

    private BatchResult executeBatch(long cutoff, int batchSize) {
        List<UUID> candidates = repository.findEligibleForUpdate(cutoff, batchSize);
        if (candidates.isEmpty()) {
            return new BatchResult(0, 0);
        }

        candidates.forEach(operationalDataResetter::deleteOperationalData);
        int tokens = repository.deleteTokens(candidates);
        int users = repository.hardDeleteEligibleUsers(candidates, cutoff);
        if (users != candidates.size()) {
            throw new IllegalStateException("Eligible account changed during physical purge");
        }
        return new BatchResult(users, tokens);
    }

    private void validateLimits(int batchSize, int maxBatches) {
        if (batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException("Batch size and maximum batches must be positive");
        }
    }

    private record BatchResult(int users, int tokens) {
    }
}
