package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class CategoryControllerTest extends BaseTest {

    private String token;

    @BeforeEach
    void setupUser() {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("cat_user");
        user.setEmail("cat@test.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email("cat@test.com").password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }

    @Test
    @DisplayName("Deve criar categoria com sucesso")
    void shouldCreateCategory() {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Alimentação");
        dto.setCategoryType("DESPESA");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/categories")
                .then()
                .statusCode(200)
                .body("name", is("Alimentação"))
                .body("id", notNullValue());
    }
}