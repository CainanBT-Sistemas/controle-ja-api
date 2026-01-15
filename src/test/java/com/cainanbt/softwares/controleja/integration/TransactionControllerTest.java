package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

public class TransactionControllerTest extends BaseTest {

    private String token;
    private UUID accountId;
    private UUID categoryId;

    @BeforeEach
    void setupUserAndData() {
        // 1. Cria Usuário Único
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "trans_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Transactor " + unique);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        // 2. Loga
        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        // 3. Cria uma Categoria para usar na transação
        CategoryDTO cat = new CategoryDTO();
        cat.setName("Lazer");
        cat.setCategoryType("DESPESA");
        categoryId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cat).post("/categories")
                .then().statusCode(200).extract().as(CategoryResponseDTO.class).getId();

        // 4. Cria uma Conta com saldo inicial de 1000
        AccountDTO acc = new AccountDTO();
        acc.setName("Carteira Teste");
        acc.setType("WALLET");
        acc.setInitialBalance(new BigDecimal("1000.00"));
        accountId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();
    }

    @Test
    @DisplayName("Deve criar DESPESA e debitar do saldo")
    void shouldCreateExpenseAndDebitBalance() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Pizza");
        dto.setType("DESPESA");
        dto.setAmount(new BigDecimal("100.00"));
        dto.setDate(System.currentTimeMillis());
        dto.setPaid(true); // Pago agora!
        dto.setAccountId(accountId);
        dto.setCategoryId(categoryId);

        // 1. Cria a transação
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/transactions")
                .then()
                .statusCode(200)
                .body("amount", is(100.0f))
                .body("accountName", is("Carteira Teste"));

        // 2. Verifica se o saldo caiu para 900 (1000 - 100)
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/accounts")
                .then()
                .statusCode(200)
                .body("[0].currentBalance", is(900.0f));
    }

    @Test
    @DisplayName("Deve criar RECEITA e creditar no saldo")
    void shouldCreateIncomeAndCreditBalance() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Salário");
        dto.setType("RECEITA");
        dto.setAmount(new BigDecimal("500.00"));
        dto.setDate(System.currentTimeMillis());
        dto.setPaid(true);
        dto.setAccountId(accountId);
        dto.setCategoryId(categoryId);

        // 1. Cria transação
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(dto).post("/transactions").then().statusCode(200);

        // 2. Verifica saldo (1000 + 500 = 1500)
        given().header("Authorization", "Bearer " + token).get("/accounts")
                .then().body("[0].currentBalance", is(1500.0f));
    }
}
