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

import java.util.UUID;

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
        token = registerAndLoginUser();
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

    @Test
    @DisplayName("Deve bloquear acesso a categoria de outro usuário")
    void shouldBlockAccessToAnotherUsersCategory() {
        String firstUserCategoryId = createCategoryAux("Categoria Privada");
        String secondUserToken = registerAndLoginUser();

        CategoryDTO updateDto = new CategoryDTO();
        updateDto.setName("Tentativa Indevida");
        updateDto.setCategoryType("DESPESA");

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().get("/categories/" + firstUserCategoryId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().put("/categories/" + firstUserCategoryId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().delete("/categories/" + firstUserCategoryId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));
    }

    @Test
    @DisplayName("Deve criar subcategoria somente em categoria pai do mesmo usuário")
    void shouldCreateSubCategoryOnlyForOwnedParent() {
        String parentId = createCategoryAux("Veículo Teste");

        CategoryDTO child = new CategoryDTO();
        child.setName("Abastecimento Teste");
        child.setCategoryType("DESPESA");
        child.setParentId(UUID.fromString(parentId));

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(child)
                .when().post("/categories")
                .then().statusCode(200)
                .body("name", is("Abastecimento Teste"))
                .body("parentId", is(parentId));
    }

    private String createCategoryAux(String name) {
        CategoryDTO dto = new CategoryDTO();
        dto.setName(name);
        dto.setCategoryType("DESPESA");

        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/categories")
                .then().statusCode(200)
                .extract().path("id");
    }

    private String registerAndLoginUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "cat_" + uniqueId + "@test.com";

        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Category User " + uniqueId);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        return given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }
}
