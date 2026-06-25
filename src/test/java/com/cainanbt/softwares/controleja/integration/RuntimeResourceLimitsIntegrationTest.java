package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RuntimeResourceLimitsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void shouldUseConservativeDatabaseAndWebThreadDefaults() {
        HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, dataSource);

        assertEquals(5, hikari.getMaximumPoolSize());
        assertEquals(1, hikari.getMinimumIdle());
        assertEquals(32, environment.getProperty("server.tomcat.threads.max", Integer.class));
        assertEquals(2, environment.getProperty("server.tomcat.threads.min-spare", Integer.class));
        assertEquals(100, environment.getProperty("server.tomcat.max-connections", Integer.class));
        assertEquals(50, environment.getProperty("server.tomcat.accept-count", Integer.class));
    }
}
