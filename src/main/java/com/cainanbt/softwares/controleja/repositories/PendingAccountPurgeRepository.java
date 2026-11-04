package com.cainanbt.softwares.controleja.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PendingAccountPurgeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public List<UUID> findEligibleForUpdate(long cutoff, int batchSize) {
        return jdbc.query("""
                        SELECT id
                          FROM users
                         WHERE deleted_at IS NOT NULL
                           AND deleted_at <= :cutoff
                         ORDER BY deleted_at, id
                         LIMIT :batchSize
                           FOR UPDATE SKIP LOCKED
                        """,
                parameters(cutoff, batchSize),
                (result, row) -> result.getObject("id", UUID.class));
    }

    public List<UUID> findEligibleForProjection(
            long cutoff,
            int batchSize,
            Set<UUID> excluded
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id
                  FROM users
                 WHERE deleted_at IS NOT NULL
                   AND deleted_at <= :cutoff
                """);
        MapSqlParameterSource parameters = parameters(cutoff, batchSize);
        if (!excluded.isEmpty()) {
            sql.append(" AND id NOT IN (:excluded)");
            parameters.addValue("excluded", excluded);
        }
        sql.append(" ORDER BY deleted_at, id LIMIT :batchSize");
        return jdbc.query(sql.toString(), parameters,
                (result, row) -> result.getObject("id", UUID.class));
    }

    public int countTokens(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_action_tokens WHERE user_id IN (:userIds)",
                new MapSqlParameterSource("userIds", userIds),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    public int deleteTokens(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        return jdbc.update(
                "DELETE FROM account_action_tokens WHERE user_id IN (:userIds)",
                new MapSqlParameterSource("userIds", userIds)
        );
    }

    public int hardDeleteEligibleUsers(Collection<UUID> userIds, long cutoff) {
        if (userIds.isEmpty()) {
            return 0;
        }
        return jdbc.update("""
                        DELETE FROM users
                         WHERE id IN (:userIds)
                           AND deleted_at IS NOT NULL
                           AND deleted_at <= :cutoff
                        """,
                new MapSqlParameterSource()
                        .addValue("userIds", userIds)
                        .addValue("cutoff", cutoff)
        );
    }

    private MapSqlParameterSource parameters(long cutoff, int batchSize) {
        return new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("batchSize", batchSize);
    }
}
