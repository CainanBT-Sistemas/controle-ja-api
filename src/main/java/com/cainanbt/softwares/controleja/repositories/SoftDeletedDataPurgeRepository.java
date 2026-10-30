package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.services.retention.PurgePlan;
import com.cainanbt.softwares.controleja.services.retention.PurgeTable;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SoftDeletedDataPurgeRepository {
    private static final long ADVISORY_LOCK_KEY = 0x434A445052474C31L;

    private static final Map<PurgeTable, List<Dependency>> DEPENDENCIES = dependencies();

    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;

    public PurgePlan planBatch(
            long cutoff,
            int batchSize,
            Map<PurgeTable, Set<UUID>> virtuallyRemoved,
            Map<PurgeTable, Set<UUID>> selectionExcluded
    ) {
        EnumMap<PurgeTable, List<UUID>> candidates = new EnumMap<>(PurgeTable.class);
        for (PurgeTable table : PurgeTable.values()) {
            Set<UUID> excluded = new LinkedHashSet<>(virtuallyRemoved.getOrDefault(table, Set.of()));
            excluded.addAll(selectionExcluded.getOrDefault(table, Set.of()));
            candidates.put(
                    table,
                    findCandidates(table, cutoff, batchSize, excluded)
            );
        }
        EnumMap<PurgeTable, List<UUID>> considered = new EnumMap<>(candidates);

        boolean changed;
        do {
            changed = false;
            for (PurgeTable table : PurgeTable.values()) {
                List<UUID> filtered = filterBlocked(table, candidates, virtuallyRemoved);
                if (filtered.size() != candidates.get(table).size()) {
                    candidates.put(table, filtered);
                    changed = true;
                }
            }
        } while (changed);

        int rankingCount = countByForeignKey(
                "gas_station_rankings",
                "gas_station_id",
                candidates.get(PurgeTable.GAS_STATIONS)
        );
        int vehicleLogCount = tableExists("vehicle_logs")
                ? countByForeignKey("vehicle_logs", "vehicle_id", candidates.get(PurgeTable.VEHICLES))
                : 0;
        return new PurgePlan(candidates, considered, rankingCount, vehicleLogCount);
    }

    public boolean tryAdvisoryLock(Connection connection) throws SQLException {
        return executeLockQuery(connection, "SELECT pg_try_advisory_lock(?)");
    }

    public boolean releaseAdvisoryLock(Connection connection) throws SQLException {
        return executeLockQuery(connection, "SELECT pg_advisory_unlock(?)");
    }

    public int clearEligibleInvoiceTransactionCycles(List<UUID> transactionIds, List<UUID> invoiceIds) {
        if (transactionIds.isEmpty() || invoiceIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("transactionIds", transactionIds)
                .addValue("invoiceIds", invoiceIds);
        int transactions = namedJdbc.update("""
                UPDATE transactions
                   SET target_invoice_id = NULL
                 WHERE id IN (:transactionIds)
                   AND target_invoice_id IN (:invoiceIds)
                """, parameters);
        int invoices = namedJdbc.update("""
                UPDATE invoicess
                   SET transaction_id = NULL
                 WHERE id IN (:invoiceIds)
                   AND transaction_id IN (:transactionIds)
                """, parameters);
        return transactions + invoices;
    }

    public int delete(PurgeTable table, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return namedJdbc.update(
                "DELETE FROM " + table.tableName() + " WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids)
        );
    }

    public int deleteGasStationRankings(Collection<UUID> stationIds) {
        return deleteByForeignKey("gas_station_rankings", "gas_station_id", stationIds);
    }

    public int deleteVehicleLogs(Collection<UUID> vehicleIds) {
        if (!tableExists("vehicle_logs")) {
            return 0;
        }
        return deleteByForeignKey("vehicle_logs", "vehicle_id", vehicleIds);
    }

    boolean tableExists(String tableName) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM information_schema.tables
                     WHERE table_schema = current_schema()
                       AND table_name = ?
                )
                """, Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    private List<UUID> findCandidates(PurgeTable table, long cutoff, int batchSize, Set<UUID> excluded) {
        StringBuilder sql = new StringBuilder("SELECT id FROM ")
                .append(table.tableName())
                .append(" WHERE deleted_at IS NOT NULL AND deleted_at <= :cutoff");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("batchSize", batchSize);
        if (!excluded.isEmpty()) {
            sql.append(" AND id NOT IN (:excluded)");
            parameters.addValue("excluded", excluded);
        }
        sql.append(" ORDER BY deleted_at, id LIMIT :batchSize");
        return namedJdbc.query(sql.toString(), parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private List<UUID> filterBlocked(
            PurgeTable target,
            Map<PurgeTable, List<UUID>> candidates,
            Map<PurgeTable, Set<UUID>> virtuallyRemoved
    ) {
        List<UUID> targetIds = candidates.get(target);
        List<Dependency> dependencies = DEPENDENCIES.getOrDefault(target, List.of());
        if (targetIds.isEmpty() || dependencies.isEmpty()) {
            return targetIds;
        }

        StringBuilder sql = new StringBuilder("SELECT candidate.id FROM ")
                .append(target.tableName())
                .append(" candidate WHERE candidate.id IN (:targetIds)");
        MapSqlParameterSource parameters = new MapSqlParameterSource("targetIds", targetIds);

        for (int index = 0; index < dependencies.size(); index++) {
            Dependency dependency = dependencies.get(index);
            Set<UUID> allowed = new LinkedHashSet<>(candidates.get(dependency.source()));
            allowed.addAll(virtuallyRemoved.getOrDefault(dependency.source(), Set.of()));
            sql.append(" AND NOT EXISTS (SELECT 1 FROM ")
                    .append(dependency.source().tableName())
                    .append(" dependent WHERE dependent.")
                    .append(dependency.foreignKeyColumn())
                    .append(" = candidate.id");
            if (!allowed.isEmpty()) {
                String parameterName = "allowed" + index;
                sql.append(" AND dependent.id NOT IN (:").append(parameterName).append(")");
                parameters.addValue(parameterName, allowed);
            }
            sql.append(')');
        }
        sql.append(" ORDER BY candidate.deleted_at, candidate.id");
        return namedJdbc.query(sql.toString(), parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private int countByForeignKey(String table, String column, Collection<UUID> parentIds) {
        if (parentIds.isEmpty()) {
            return 0;
        }
        return namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IN (:ids)",
                new MapSqlParameterSource("ids", parentIds),
                Integer.class
        );
    }

    private int deleteByForeignKey(String table, String column, Collection<UUID> parentIds) {
        if (parentIds.isEmpty()) {
            return 0;
        }
        return namedJdbc.update(
                "DELETE FROM " + table + " WHERE " + column + " IN (:ids)",
                new MapSqlParameterSource("ids", parentIds)
        );
    }

    private boolean executeLockQuery(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static Map<PurgeTable, List<Dependency>> dependencies() {
        EnumMap<PurgeTable, List<Dependency>> dependencies = new EnumMap<>(PurgeTable.class);
        dependencies.put(PurgeTable.TRANSACTIONS, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "parent_transaction_id"),
                new Dependency(PurgeTable.INVOICES, "transaction_id")
        ));
        dependencies.put(PurgeTable.INVOICES, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "target_invoice_id"),
                new Dependency(PurgeTable.INSTALLMENT_PLAN, "invoices_id"),
                new Dependency(PurgeTable.INSTALLMENT_PLAN, "advanced_from_invoice_id")
        ));
        dependencies.put(PurgeTable.GAS_STATIONS, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "gas_station_id")
        ));
        dependencies.put(PurgeTable.RECURRENCE_RULES, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "recurrence_rule_id")
        ));
        dependencies.put(PurgeTable.CREDIT_CARDS, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "credit_card_id"),
                new Dependency(PurgeTable.INVOICES, "credit_card_id")
        ));
        dependencies.put(PurgeTable.VEHICLES, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "vehicle_id")
        ));
        dependencies.put(PurgeTable.CATEGORIES, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "category_id"),
                new Dependency(PurgeTable.RECURRENCE_RULES, "category_id"),
                new Dependency(PurgeTable.CATEGORIES, "sub_category_id")
        ));
        dependencies.put(PurgeTable.ACCOUNTS, List.of(
                new Dependency(PurgeTable.TRANSACTIONS, "account_id"),
                new Dependency(PurgeTable.RECURRENCE_RULES, "account_id"),
                new Dependency(PurgeTable.RECURRENCE_RULES, "target_account_id"),
                new Dependency(PurgeTable.CREDIT_CARDS, "account_id")
        ));
        return Map.copyOf(dependencies);
    }

    private record Dependency(PurgeTable source, String foreignKeyColumn) {
    }
}
