package com.cainanbt.softwares.controleja.services.retention;

public record PendingAccountPurgeResult(
        boolean dryRun,
        int batches,
        long users,
        long accountActionTokens
) {
}
