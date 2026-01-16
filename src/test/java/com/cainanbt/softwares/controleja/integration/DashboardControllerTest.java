package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class DashboardControllerTest extends BaseTest {

    private String token;
    private UUID walletId;
    private UUID catFoodId;
    private UUID catCarId;

    @BeforeEach
    void setup() {
        // 1. User & Login
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "dash_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Dashboard User");
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        // 2. Conta
        AccountDTO acc = new AccountDTO();
        acc.setName("Carteira");
        acc.setType(AccountType.WALLET);
        acc.setInitialBalance(new BigDecimal("5000.00"));
        walletId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(acc).post("/accounts").then().extract().as(AccountResponseDTO.class).getId();

        // 3. Categorias
        CategoryDTO c1 = new CategoryDTO();
        c1.setName("Comida");
        c1.setCategoryType("DESPESA");
        catFoodId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(c1).post("/categories").then().extract().as(CategoryResponseDTO.class).getId();

        CategoryDTO c2 = new CategoryDTO();
        c2.setName("Carro");
        c2.setCategoryType("DESPESA");
        catCarId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(c2).post("/categories").then().extract().as(CategoryResponseDTO.class).getId();

        // 4. Cria Transações (Massa de Dados)
        createTx("Burguer", new BigDecimal("50.00"), catFoodId, TransactionType.DESPESA);
        createTx("Pizza", new BigDecimal("100.00"), catFoodId, TransactionType.DESPESA);
        createTx("Gasolina", new BigDecimal("200.00"), catCarId, TransactionType.DESPESA);
        createTx("Salario", new BigDecimal("1000.00"), catFoodId, TransactionType.RECEITA); // Receita fake
    }

    private void createTx(String name, BigDecimal amount, UUID catId, TransactionType type) {
        TransactionDTO dto = new TransactionDTO();
        dto.setName(name);
        dto.setAmount(amount);
        dto.setCategoryId(catId);
        dto.setAccountId(walletId);
        dto.setType(type);
        dto.setPaid(true);
        dto.setDate(System.currentTimeMillis());
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(dto).post("/transactions").then().statusCode(200);
    }

    @Test
    @DisplayName("Deve calcular resumo financeiro corretamente")
    void shouldReturnCorrectSummary() {
        long now = System.currentTimeMillis();
        long start = now - 100000;
        long end = now + 100000;

        given()
                .header("Authorization", "Bearer " + token)
                .param("start", start)
                .param("end", end)
                .when()
                .get("/dashboard/summary")
                .then()
                .statusCode(200)
                .body("totalIncome", is(1000.0f))
                .body("totalExpense", is(350.0f)) // 50 + 100 + 200
                .body("balance", is(650.0f));
    }

    @Test
    @DisplayName("Deve agrupar despesas por categoria")
    void shouldGroupExpensesByCategory() {
        long now = System.currentTimeMillis();

        given()
                .header("Authorization", "Bearer " + token)
                .param("start", now - 100000)
                .param("end", now + 100000)
                .when()
                .get("/dashboard/expenses-category")
                .then()
                .statusCode(200)
                .body("size()", is(2)) // Comida e Carro
                .body("find { it.label == 'Comida' }.value", is(150.0f))
                .body("find { it.label == 'Carro' }.value", is(200.0f));
    }
}