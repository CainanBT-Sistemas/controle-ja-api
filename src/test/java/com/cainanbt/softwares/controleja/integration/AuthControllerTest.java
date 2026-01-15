package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
public class AuthControllerTest extends BaseTest {

    @BeforeEach
    void setup() {
        log.info("Stating AuthControllerTest");
    }

    @Test
    @DisplayName("Deve realizar login com sucesso e retornar tokens")
    void shouldLoginSuccessfully() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("login_user");
        user.setEmail("login@teste.com");
        user.setPassword("123456");

        given()
                .contentType(ContentType.JSON)
                .body(user)
                .post("/users/register")
                .then()
                .statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder()
                .email("login@teste.com")
                .password("123456")
                .build();

        given()
                .contentType(ContentType.JSON)
                .body(login)
                .when()
                .post("/auth")
                .then()
                .statusCode(200)
                .body("tokens.accessToken", notNullValue())
                .body("tokens.refreshToken", notNullValue());
    }

    @Test
    @DisplayName("Deve retornar 400 ao tentar logar com senha errada")
    void shouldReturn400_WhenPasswordIsWrong() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("wrong_pass_user");
        user.setEmail("wrong@teste.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder()
                .email("wrong@teste.com")
                .password("senha_errada")
                .build();

        given()
                .contentType(ContentType.JSON)
                .body(login)
                .when()
                .post("/auth")
                .then()
                .statusCode(400);
    }
}
