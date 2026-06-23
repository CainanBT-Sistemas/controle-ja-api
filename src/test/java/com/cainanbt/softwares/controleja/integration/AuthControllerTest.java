package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Slf4j
public class AuthControllerTest extends BaseTest {

    @BeforeEach
    void setup() {
        log.info("Starting AuthControllerTest");
    }

    @Test
    @DisplayName("Deve realizar login com sucesso e retornar tokens")
    void shouldLoginSuccessfully() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("login_user");
        user.setEmail("login@teste.com");
        user.setPassword("123456");

        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder()
                .email("login@teste.com")
                .password("123456")
                .build();

        given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("username", is("login_user"))
                .body("email", is("login@teste.com"))
                .body("createdAt", notNullValue())
                .body("tokens.accessToken", notNullValue())
                .body("tokens.refreshToken", notNullValue());
    }

    @Test
    @DisplayName("Deve cadastrar usuario sem retornar tokens de sessao")
    void shouldRegisterWithoutAuthenticatedSession() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("register_user");
        user.setEmail("register@teste.com");
        user.setPassword("123456");

        given().contentType(ContentType.JSON).body(user)
                .when().post("/users/register")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("username", is("register_user"))
                .body("email", is("register@teste.com"))
                .body("createdAt", notNullValue())
                .body("tokens", nullValue());
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

        given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(400)
                .body("title", is("Acesso negado"));
    }

    @Test
    @DisplayName("Deve realizar auto-login com refresh token válido e retornar novos tokens")
    void shouldAutoLoginSuccessfully() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("auto_user");
        user.setEmail("auto@teste.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email("auto@teste.com").password("123456").build();

        String refreshToken = given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(200)
                .extract().path("tokens.refreshToken");

        // A CORREÇÃO: Enviando chave 'token' como esperado pelo TokenLoginDTO
        given().contentType(ContentType.JSON).body(Collections.singletonMap("token", refreshToken))
                .when().post("/auth/auto-login")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("username", is("auto_user"))
                .body("email", is("auto@teste.com"))
                .body("createdAt", notNullValue())
                .body("tokens.accessToken", notNullValue())
                .body("tokens.refreshToken", notNullValue());
    }

    @Test
    @DisplayName("Deve retornar 400 ao tentar auto-login com token inválido")
    void shouldReturn400_WhenAutoLoginWithInvalidToken() {
        given().contentType(ContentType.JSON).body(Collections.singletonMap("token", "invalid.token.value"))
                .when().post("/auth/auto-login")
                .then().statusCode(400);
    }
}
