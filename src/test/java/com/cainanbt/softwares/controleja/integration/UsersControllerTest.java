package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.PasswordChangeDTO;
import com.cainanbt.softwares.controleja.dtos.UpdateProfileDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@Slf4j
public class UsersControllerTest extends BaseTest {

    private String token;
    private String userId;

    @BeforeEach
    void setup() {
        // Blindagem Absoluta: Randomiza tudo para não chocar com rodadas anteriores no Testcontainers
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "cainan_" + unique + "@teste.com";

        InsertUpdateUserDTO validUser = new InsertUpdateUserDTO();
        validUser.setUsername("User_" + unique); // Agora o username também é único
        validUser.setPassword("123456");
        validUser.setEmail(email);

        userId = given().contentType(ContentType.JSON).body(validUser)
                .when().post("/users/register")
                .then().statusCode(200)
                .extract().path("id");

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }

    @Test
    @DisplayName("Deve barrar usuário com email inválido")
    void shouldReturn400_WhenEmailIsInvalid() {
        InsertUpdateUserDTO invalidUser = new InsertUpdateUserDTO();
        invalidUser.setUsername("cainanbt_errado");
        invalidUser.setPassword("123456");
        invalidUser.setEmail("email-errado-sem-arroba");

        given().contentType(ContentType.JSON).body(invalidUser)
                .when().post("/users/register")
                .then().statusCode(400)
                .body("title", is("Erro de Validação"));
    }

    @Test
    @DisplayName("Deve acessar todas as rotas restritas de usuário")
    void shouldHitAllRestrictedUserEndpoints() {
        UpdateProfileDTO profileDTO = new UpdateProfileDTO();
        profileDTO.setUsername("Cainan Alterado");
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(profileDTO)
                .when().put("/users/profile")
                .then().statusCode(200)
                .body("username", is("Cainan Alterado"));

        PasswordChangeDTO passDTO = new PasswordChangeDTO();
        passDTO.setCurrentPassword("123456");
        passDTO.setNewPassword("654321");
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(passDTO)
                .when().put("/users/change-password")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().post("/users/" + userId + "/reset")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/users/" + userId)
                .then().statusCode(200)
                .body("message", is("Usuário excluído com sucesso"));
    }
}
