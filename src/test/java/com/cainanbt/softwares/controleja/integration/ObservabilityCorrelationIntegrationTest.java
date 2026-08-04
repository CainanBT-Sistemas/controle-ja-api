package com.cainanbt.softwares.controleja.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.configs.CorrelationId;
import com.cainanbt.softwares.controleja.configs.SecurityErrorResponseWriter;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.exceptions.handle.RestExceptionHandler;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.exceptions.models.EntityNotFoundException;
import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityCorrelationIntegrationTest extends BaseTest {

    private static final String VALID_CORRELATION_ID = "po-test_123.abc";

    @Test
    void shouldPreserveValidCorrelationIdInHeaderErrorBodyAndLog() {
        ListAppender<ILoggingEvent> appender = attachAppender(RestExceptionHandler.class);
        try {
            Response response = given()
                    .contentType(ContentType.JSON)
                    .header(CorrelationId.HEADER_NAME, VALID_CORRELATION_ID)
                    .header("Authorization", "Bearer " + registerAndLogin())
                    .when().get("/observability-test/bad-request?token=hidden-token&email=hidden@test.com");

            response.then()
                    .statusCode(400)
                    .header(CorrelationId.HEADER_NAME, VALID_CORRELATION_ID)
                    .body("correlationId", is(VALID_CORRELATION_ID))
                    .body("code", is(400));

            ILoggingEvent event = singleEvent(appender);
            assertEquals(Level.WARN, event.getLevel());
            assertEquals(VALID_CORRELATION_ID, event.getMDCPropertyMap().get(CorrelationId.MDC_KEY));
            assertTrue(event.getFormattedMessage().contains("method=GET"));
            assertTrue(event.getFormattedMessage().contains("path=/controle_ja_api/v1/observability-test/bad-request"));
            assertFalse(event.getFormattedMessage().contains("hidden-token"));
            assertFalse(event.getFormattedMessage().contains("hidden@test.com"));
            assertNull(event.getThrowableProxy());
        } finally {
            detachAppender(RestExceptionHandler.class, appender);
        }
    }

    @Test
    void shouldGenerateUuidWhenCorrelationIdIsMissingOrInvalid() {
        Response missing = given()
                .when().get("/accounts");

        String generated = missing.then()
                .statusCode(401)
                .extract().header(CorrelationId.HEADER_NAME);
        assertDoesNotThrow(() -> UUID.fromString(generated));
        missing.then().body("correlationId", is(generated));

        Response invalid = given()
                .header(CorrelationId.HEADER_NAME, "invalid header with spaces")
                .when().get("/accounts");

        String replacement = invalid.then()
                .statusCode(401)
                .extract().header(CorrelationId.HEADER_NAME);
        assertDoesNotThrow(() -> UUID.fromString(replacement));
        invalid.then()
                .body("correlationId", is(replacement))
                .body("correlationId", not(is("invalid header with spaces")));
    }

    @Test
    void shouldIncludeCorrelationIdIn400401403404And500Responses() {
        String token = registerAndLogin();

        assertErrorContainsCorrelationId(400, "/observability-test/bad-request", token);
        assertErrorContainsCorrelationId(403, "/observability-test/forbidden", token);
        assertErrorContainsCorrelationId(404, "/observability-test/not-found", token);
        assertErrorContainsCorrelationId(500, "/observability-test/unexpected", token);

        given()
                .header(CorrelationId.HEADER_NAME, "auth-401")
                .when().get("/accounts")
                .then()
                .statusCode(401)
                .header(CorrelationId.HEADER_NAME, "auth-401")
                .body("correlationId", is("auth-401"));
    }

    @Test
    void shouldNotLeakMdcBetweenSequentialRequests() {
        ListAppender<ILoggingEvent> appender = attachAppender(RestExceptionHandler.class);
        try {
            String token = registerAndLogin();

            Response first = given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/observability-test/bad-request");
            Response second = given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/observability-test/bad-request");

            String firstId = first.then().statusCode(400).extract().header(CorrelationId.HEADER_NAME);
            String secondId = second.then().statusCode(400).extract().header(CorrelationId.HEADER_NAME);

            assertNotEquals(firstId, secondId);
            assertEquals(2, appender.list.size());
            assertEquals(firstId, appender.list.get(0).getMDCPropertyMap().get(CorrelationId.MDC_KEY));
            assertEquals(secondId, appender.list.get(1).getMDCPropertyMap().get(CorrelationId.MDC_KEY));
        } finally {
            detachAppender(RestExceptionHandler.class, appender);
        }
    }

    @Test
    void shouldAllowAndExposeCorrelationIdInCorsConfiguration() {
        given()
                .header("Origin", "http://localhost:51038")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type, X-Correlation-Id")
                .when().options("/accounts")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Headers", containsString(CorrelationId.HEADER_NAME));

        given()
                .basePath("")
                .header("Origin", "http://localhost:51038")
                .when().get("/actuator/health")
                .then()
                .statusCode(200)
                .header("Access-Control-Expose-Headers", containsString(CorrelationId.HEADER_NAME));
    }

    @Test
    void shouldNotCreateOperationalLogForSuccessfulRequest() {
        ListAppender<ILoggingEvent> restAppender = attachAppender(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> securityAppender = attachAppender(SecurityErrorResponseWriter.class);
        try {
            given()
                    .basePath("")
                    .header(CorrelationId.HEADER_NAME, "success-no-log")
                    .when().get("/actuator/health")
                    .then()
                    .statusCode(200)
                    .header(CorrelationId.HEADER_NAME, "success-no-log");

            assertTrue(restAppender.list.isEmpty());
            assertTrue(securityAppender.list.isEmpty());
        } finally {
            detachAppender(RestExceptionHandler.class, restAppender);
            detachAppender(SecurityErrorResponseWriter.class, securityAppender);
        }
    }

    @Test
    void shouldLog4xxWithoutStackTraceAnd5xxExactlyOnceWithStackTrace() {
        String token = registerAndLogin();
        ListAppender<ILoggingEvent> appender = attachAppender(RestExceptionHandler.class);
        ListAppender<ILoggingEvent> resolverAppender = attachAppender(ExceptionHandlerExceptionResolver.class);
        try {
            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/observability-test/bad-request")
                    .then().statusCode(400);

            assertEquals(1, appender.list.size());
            assertTrue(resolverAppender.list.isEmpty());
            assertEquals(Level.WARN, appender.list.get(0).getLevel());
            assertNull(appender.list.get(0).getThrowableProxy());

            appender.list.clear();
            resolverAppender.list.clear();

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get("/observability-test/unexpected")
                    .then().statusCode(500);

            assertEquals(1, appender.list.size());
            assertTrue(resolverAppender.list.isEmpty());
            assertEquals(Level.ERROR, appender.list.get(0).getLevel());
            assertNotNull(appender.list.get(0).getThrowableProxy());
        } finally {
            detachAppender(RestExceptionHandler.class, appender);
            detachAppender(ExceptionHandlerExceptionResolver.class, resolverAppender);
        }
    }

    @Test
    void shouldIncludeCorrelationIdInSecurityForbiddenWriter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/controle_ja_api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.slf4j.MDC.put(CorrelationId.MDC_KEY, "forbidden-test");
        try {
            SecurityErrorResponseWriter.writeForbidden(request, response);
        } finally {
            org.slf4j.MDC.remove(CorrelationId.MDC_KEY);
        }

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertEquals("forbidden-test", response.getHeader(CorrelationId.HEADER_NAME));
        assertTrue(response.getContentAsString().contains("\"correlationId\":\"forbidden-test\""));
    }

    private void assertErrorContainsCorrelationId(int status, String path, String token) {
        String correlationId = "error-" + status;
        given()
                .header(CorrelationId.HEADER_NAME, correlationId)
                .header("Authorization", "Bearer " + token)
                .when().get(path)
                .then()
                .statusCode(status)
                .header(CorrelationId.HEADER_NAME, correlationId)
                .body("correlationId", is(correlationId))
                .body("code", is(status));
    }

    private String registerAndLogin() {
        String suffix = UUID.randomUUID().toString();
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("observability_user");
        user.setEmail("observability-" + suffix + "@test.com");
        user.setPassword("123456");

        given().contentType(ContentType.JSON).body(user)
                .post("/users/register")
                .then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder()
                .email(user.getEmail())
                .password("123456")
                .build();

        return given().contentType(ContentType.JSON).body(login)
                .post("/auth")
                .then().statusCode(200)
                .extract().path("tokens.accessToken");
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> loggerClass) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        logger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent singleEvent(ListAppender<ILoggingEvent> appender) {
        assertEquals(1, appender.list.size());
        return appender.list.get(0);
    }

    @TestConfiguration
    static class ObservabilityTestConfiguration {

        @Bean
        ObservabilityTestController observabilityTestController() {
            return new ObservabilityTestController();
        }
    }

    @RestController
    @RequestMapping("/controle_ja_api/v1/observability-test")
    static class ObservabilityTestController {

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException(ConstsMessages.ERROR_TITLE, "Erro seguro de teste");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Forbidden", "Acesso negado.");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new EntityNotFoundException(ConstsMessages.ERROR_TITLE, "Registro nao encontrado");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("Erro interno de teste");
        }
    }
}
