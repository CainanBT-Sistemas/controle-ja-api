package com.cainanbt.softwares.controleja.services.retention;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PurgePlan {
    private final EnumMap<PurgeTable, List<UUID>> ids;
    private final EnumMap<PurgeTable, List<UUID>> consideredIds;
    private final int gasStationRankingCount;
    private final int vehicleLogCount;

    public PurgePlan(
            Map<PurgeTable, List<UUID>> ids,
            Map<PurgeTable, List<UUID>> consideredIds,
            int gasStationRankingCount,
            int vehicleLogCount
    ) {
        this.ids = new EnumMap<>(PurgeTable.class);
        this.consideredIds = new EnumMap<>(PurgeTable.class);
        for (PurgeTable table : PurgeTable.values()) {
            this.ids.put(table, List.copyOf(ids.getOrDefault(table, List.of())));
            this.consideredIds.put(table, List.copyOf(consideredIds.getOrDefault(table, List.of())));
        }
        this.gasStationRankingCount = gasStationRankingCount;
        this.vehicleLogCount = vehicleLogCount;
    }

    public List<UUID> ids(PurgeTable table) {
        return ids.get(table);
    }

    public Map<PurgeTable, List<UUID>> allIds() {
        return Collections.unmodifiableMap(ids);
    }

    public Map<PurgeTable, List<UUID>> consideredIds() {
        return Collections.unmodifiableMap(consideredIds);
    }

    public boolean isEmpty() {
        return ids.values().stream().allMatch(List::isEmpty);
    }

    public boolean consideredNothing() {
        return consideredIds.values().stream().allMatch(List::isEmpty);
    }

    public Map<String, Long> projectedCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PurgeTable table : PurgeTable.values()) {
            counts.put(table.tableName(), (long) ids(table).size());
        }
        counts.put("gas_station_rankings", (long) gasStationRankingCount);
        counts.put("vehicle_logs", (long) vehicleLogCount);
        return counts;
    }
}
