package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
public class UsersControllerTest extends BaseTest {

    @BeforeEach
    void setup() {
        log.info("Stating UsersControllerTest");
    }

    @Test
    @DisplayName("Deve retornar erro 400 quando criar usuário com email inválido")
    void shouldReturn400_WhenEmailIsInvalid() {
        InsertUpdateUserDTO invalidUser = new InsertUpdateUserDTO();
        invalidUser.setUsername("cainanbt");
        invalidUser.setPassword("123456");
        invalidUser.setEmail("email-errado-sem-arroba"); // O BUG QUE VOCÊ ACHOU

        given()
                .contentType(ContentType.JSON)
                .body(invalidUser)
                .when()
                .post("/users/register")
                .then()
                .statusCode(400) // Agora deve dar 400!
                .body("title", is("Erro de Validação"));
    }

    @Test
    @DisplayName("Deve criar usuário com sucesso quando dados válidos")
    void shouldCreateUser_WhenDataIsValid() {
        InsertUpdateUserDTO validUser = new InsertUpdateUserDTO();
        validUser.setUsername("cainan_correto");
        validUser.setPassword("123456");
        validUser.setEmail("cainan@teste.com");

        given()
                .contentType(ContentType.JSON)
                .body(validUser)
                .when()
                .post("/users/register")
                .then()
                .statusCode(200)
                .body("email", is("cainan@teste.com"))
                .body("id", notNullValue());
    }
}
