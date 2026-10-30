package com.cainanbt.softwares.controleja.workers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cainanbt.softwares.controleja.configs.DataRetentionProperties;
import com.cainanbt.softwares.controleja.repositories.SoftDeletedDataPurgeRepository;
import com.cainanbt.softwares.controleja.services.retention.SoftDeletedDataPurgeService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionWorkerTest {

    @Test
    void shouldSanitizeAbsorbedFailureAndReleaseLock() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SoftDeletedDataPurgeRepository repository = mock(SoftDeletedDataPurgeRepository.class);
        SoftDeletedDataPurgeService service = mock(SoftDeletedDataPurgeService.class);
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.setDryRun(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(repository.tryAdvisoryLock(connection)).thenReturn(true);
        when(repository.releaseAdvisoryLock(connection)).thenReturn(true);
        when(service.purge(anyLong(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException(
                        "email=user@test.local token=secret amount=999.99 SQL=DELETE"
                ));

        Logger logger = (Logger) LoggerFactory.getLogger(DataRetentionWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            DataRetentionWorker worker = new DataRetentionWorker(
                    dataSource,
                    properties,
                    repository,
                    service
            );
            assertTrue(worker.runOnce().isEmpty());
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("exception=IllegalStateException"));
        assertFalse(logs.contains("user@test.local"));
        assertFalse(logs.contains("secret"));
        assertFalse(logs.contains("999.99"));
        assertFalse(logs.contains("DELETE"));
        verify(repository).releaseAdvisoryLock(connection);
    }

    @Test
    void shouldKeepSafeDisabledDefaults() {
        DataRetentionProperties properties = new DataRetentionProperties();

        assertFalse(properties.isEnabled());
        assertTrue(properties.isDryRun());
        assertEquals(60, properties.getDays());
        assertEquals(100, properties.getBatchSize());
        assertEquals(20, properties.getMaxBatchesPerRun());
    }

    @Test
    void shouldRejectRetentionBelowSixtyDays() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.setDays(59);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertFalse(validator.validate(properties).isEmpty());
    }
}
