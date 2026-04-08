package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
public class CreditCardControllerTest extends BaseTest {

    private String token;

    @BeforeEach
    void setupUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "user_" + uniqueId + "@test.com";

        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("User " + uniqueId);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }

    @Test
    @DisplayName("CRUD Completo de Cartão de Crédito")
    void shouldPerformFullCreditCardCRUD() {
        // 1. CREATE
        CreditCardDTO dto = new CreditCardDTO();
        dto.setName("Nubank Platinum");
        dto.setLimit(new BigDecimal("5000.00"));
        dto.setCloseDay(4);
        dto.setBestDay(11);

        String cardId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().post("/cards")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("name", is("Nubank Platinum"))
                .body("currentLimit", is(5000.0f))
                .extract().path("id");

        // 2. READ (List)
        given().header("Authorization", "Bearer " + token)
                .when().get("/cards")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", is("Nubank Platinum"));

        // 3. UPDATE
        dto.setName("Nubank Ultravioleta");
        dto.setLimit(new BigDecimal("10000.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().put("/cards/" + cardId)
                .then().statusCode(200)
                .body("name", is("Nubank Ultravioleta"))
                .body("totalLimit", is(10000.0f));

        // 4. DELETE
        given().header("Authorization", "Bearer " + token)
                .when().delete("/cards/" + cardId)
                .then().statusCode(200);

        // Verifica se deletou
        given().header("Authorization", "Bearer " + token)
                .when().get("/cards")
                .then().statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @DisplayName("Deve bloquear o 3º cartão (Regra do Plano Free)")
    void shouldBlockThirdCard() {
        createCardAux("Card 1");
        createCardAux("Card 2");

        CreditCardDTO dto = new CreditCardDTO();
        dto.setName("Card 3 Bloqueado");
        dto.setLimit(BigDecimal.TEN);
        dto.setCloseDay(1);
        dto.setBestDay(10);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().post("/cards")
                .then().statusCode(400)
                .body("title", is("Limite Atingido"));
    }

    private void createCardAux(String name) {
        CreditCardDTO dto = new CreditCardDTO();
        dto.setName(name);
        dto.setLimit(new BigDecimal("1000"));
        dto.setCloseDay(1);
        dto.setBestDay(10);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/cards").then().statusCode(200);
    }
}