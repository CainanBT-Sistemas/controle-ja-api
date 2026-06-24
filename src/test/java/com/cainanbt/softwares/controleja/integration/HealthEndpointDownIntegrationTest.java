package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthEndpointDownIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        RestAssured.defaultParser = Parser.JSON;
    }

    @Test
    void shouldReturnServiceUnavailableWhenDatabasePoolIsDown() {
        HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);
        hikariDataSource.close();

        given()
                .when().get("/actuator/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("size()", is(1));
    }
}
