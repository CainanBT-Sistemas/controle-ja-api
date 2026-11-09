package com.cainanbt.softwares.controleja.config;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

public class BaseTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/controle_ja_api/v1";
        RestAssured.defaultParser = Parser.JSON;
    }

    protected void verifyEmail(String email) {
        jdbcTemplate.update(
                "UPDATE users SET email_verified = true WHERE lower(email) = lower(?)",
                email
        );
    }
}

