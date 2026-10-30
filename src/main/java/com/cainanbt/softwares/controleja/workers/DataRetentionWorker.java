package com.cainanbt.softwares.controleja.workers;

import com.cainanbt.softwares.controleja.configs.DataRetentionProperties;
import com.cainanbt.softwares.controleja.repositories.SoftDeletedDataPurgeRepository;
import com.cainanbt.softwares.controleja.services.retention.PurgeRunResult;
import com.cainanbt.softwares.controleja.services.retention.SoftDeletedDataPurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.config.data-retention.enabled", havingValue = "true")
public class DataRetentionWorker {
    private final DataSource dataSource;
    private final DataRetentionProperties properties;
    private final SoftDeletedDataPurgeRepository repository;
    private final SoftDeletedDataPurgeService purgeService;

    @Scheduled(
            cron = "${app.config.data-retention.cron:0 30 4 * * *}",
            zone = "${app.config.data-retention.zone:America/Sao_Paulo}"
    )
    public void runScheduled() {
        runOnce();
    }

    public Optional<PurgeRunResult> runOnce() {
        long startedAt = System.currentTimeMillis();
        long cutoff = startedAt - Duration.ofDays(properties.getDays()).toMillis();

        try (Connection lockConnection = dataSource.getConnection()) {
            if (!repository.tryAdvisoryLock(lockConnection)) {
                return Optional.empty();
            }
            try {
                log.info("data_retention_started cutoff={} dryRun={}", cutoff, properties.isDryRun());
                PurgeRunResult result = purgeService.purge(
                        cutoff,
                        properties.isDryRun(),
                        properties.getBatchSize(),
                        properties.getMaxBatchesPerRun()
                );
                log.info(
                        "data_retention_finished durationMs={} cutoff={} dryRun={} batches={} counts={}",
                        System.currentTimeMillis() - startedAt,
                        cutoff,
                        properties.isDryRun(),
                        result.getBatches(),
                        result.getCounts()
                );
                return Optional.of(result);
            } catch (RuntimeException exception) {
                log.error(
                        "data_retention_failed cutoff={} dryRun={} exception={}",
                        cutoff,
                        properties.isDryRun(),
                        exception.getClass().getSimpleName()
                );
                return Optional.empty();
            } finally {
                releaseLock(lockConnection);
            }
        } catch (SQLException exception) {
            log.error(
                    "data_retention_lock_failed exception={}",
                    exception.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    private void releaseLock(Connection connection) throws SQLException {
        if (!repository.releaseAdvisoryLock(connection)) {
            throw new SQLException("Data retention advisory lock was not held by this connection");
        }
    }
}
