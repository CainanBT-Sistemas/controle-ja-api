package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
public class CategoryControllerTest extends BaseTest {

    private String token;

    @BeforeEach
    void setupUser() {
        log.info("Starting CategoryControllerTest");
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("cat_user");
        user.setEmail("cat@test.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email("cat@test.com").password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }

    @Test
    @DisplayName("Deve fazer o CRUD completo de Categoria para bater 100% de coverage")
    void shouldPerformFullCategoryCRUD() {
        // 1. CREATE
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Alimentação");
        dto.setCategoryType("DESPESA");
        dto.setColor("#FFFFFF");

        String categoryId = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().post("/categories")
                .then().statusCode(200)
                .body("name", is("Alimentação"))
                .body("id", notNullValue())
                .extract().path("id");

        // 2. READ (List All) - Verifica se a categoria padrão "Outros" e a nova "Alimentação" estão lá
        given().header("Authorization", "Bearer " + token)
                .when().get("/categories")
                .then().statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));

        // 3. READ (Get By ID)
        given().header("Authorization", "Bearer " + token)
                .when().get("/categories/" + categoryId)
                .then().statusCode(200)
                .body("name", is("Alimentação"));

        // 4. UPDATE
        dto.setName("Alimentação Atualizada");
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().put("/categories/" + categoryId)
                .then().statusCode(200)
                .body("name", is("Alimentação Atualizada"));

        // 5. DELETE
        given().header("Authorization", "Bearer " + token)
                .when().delete("/categories/" + categoryId)
                .then().statusCode(200)
                .body("message", is("Registro excluído com sucesso."));
    }
}