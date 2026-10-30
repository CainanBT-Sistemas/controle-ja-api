package com.cainanbt.softwares.controleja.services.retention;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PurgeRunResult {
    private final boolean dryRun;
    private final int batches;
    private final Map<String, Long> counts;

    PurgeRunResult(boolean dryRun, int batches, Map<String, Long> counts) {
        this.dryRun = dryRun;
        this.batches = batches;
        this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getBatches() {
        return batches;
    }

    public Map<String, Long> getCounts() {
        return counts;
    }

    public long getTotalCount() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
