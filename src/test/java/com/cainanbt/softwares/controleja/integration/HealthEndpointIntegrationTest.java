package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthEndpointIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        RestAssured.defaultParser = Parser.JSON;
    }

    @Test
    void shouldReturnUpWithoutAuthenticationWhenApplicationAndDatabaseAreAvailable() {
        assertNotNull(healthContributorRegistry.getContributor("db"));

        given()
                .when().get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("size()", is(1));
    }

    @Test
    void shouldExposeOnlyTheHealthActuatorEndpoint() {
        Set<String> exposedEndpointIds = webEndpointsSupplier.getEndpoints().stream()
                .map(endpoint -> endpoint.getEndpointId().toString())
                .collect(Collectors.toSet());

        assertEquals(Set.of("health"), exposedEndpointIds);
    }
}
