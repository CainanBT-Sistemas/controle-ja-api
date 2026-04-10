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
import com.cainanbt.softwares.controleja.utils.DateUtils;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class DashboardControllerTest extends BaseTest {

    private String token;
    private UUID walletId;
    private UUID catFoodId;
    private UUID catCarId;

    @BeforeEach
    void setup() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "dash_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Dashboard User");
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        AccountDTO acc = new AccountDTO();
        acc.setName("Carteira");
        acc.setType(AccountType.WALLET);
        acc.setInitialBalance(new BigDecimal("5000.00"));
        walletId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(acc).post("/accounts").then().extract().as(AccountResponseDTO.class).getId();

        CategoryDTO c1 = new CategoryDTO();
        c1.setName("Comida");
        c1.setCategoryType("DESPESA");
        catFoodId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(c1).post("/categories").then().extract().as(CategoryResponseDTO.class).getId();

        CategoryDTO c2 = new CategoryDTO();
        c2.setName("Carro");
        c2.setCategoryType("DESPESA");
        catCarId = given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(c2).post("/categories").then().extract().as(CategoryResponseDTO.class).getId();

        createTx("Burguer", new BigDecimal("50.00"), catFoodId, TransactionType.DESPESA);
        createTx("Pizza", new BigDecimal("100.00"), catFoodId, TransactionType.DESPESA);
        createTx("Gasolina", new BigDecimal("200.00"), catCarId, TransactionType.DESPESA);
        createTx("Salario", new BigDecimal("1000.00"), catFoodId, TransactionType.RECEITA);
    }

    private void createTx(String name, BigDecimal amount, UUID catId, TransactionType type) {
        TransactionDTO dto = new TransactionDTO();
        dto.setName(name);
        dto.setAmount(amount);
        dto.setCategoryId(catId);
        dto.setAccountId(walletId);
        dto.setType(type);
        dto.setPaid(true);
        dto.setDate(DateUtils.getEpochNow());
        dto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(dto).post("/transactions").then().statusCode(200);
    }

    @Test
    @DisplayName("Deve varrer todos os endpoints do Dashboard para cobertura 100%")
    void shouldHitAllDashboardEndpoints() {
        long now = DateUtils.getEpochNow();
        long start = now - 86400000L;
        long end = now + 86400000L;

        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/summary")
                .then().statusCode(200).body("totalIncome", is(1000.0f));

        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/expenses-category")
                .then().statusCode(200).body("size()", is(2));

        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/incomes-category")
                .then().statusCode(200).body("size()", is(1));

        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/evolution")
                .then().statusCode(200).body("$", notNullValue());

        // Endpoint Evolution testando o ID Categoria para atingir os 100%
        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end).param("categoryId", catFoodId)
                .when().get("/dashboard/evolution")
                .then().statusCode(200).body("$", notNullValue());

        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/fuel-comparison")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("Deve retornar full-summary com pendências e projeções corretas")
    void shouldReturnFullSummaryWithPendingAndProjections() {
        long now = DateUtils.getEpochNow();
        long start = now - 86400000L;
        long end = now + 86400000L;

        // criar pendência (despesa) não paga
        TransactionDTO unpaidExpense = new TransactionDTO();
        unpaidExpense.setName("Unpaid Bill");
        unpaidExpense.setAmount(new BigDecimal("150.00"));
        unpaidExpense.setCategoryId(catFoodId);
        unpaidExpense.setAccountId(walletId);
        unpaidExpense.setType(TransactionType.DESPESA);
        unpaidExpense.setPaid(false);
        unpaidExpense.setDate(now);
        unpaidExpense.setIsFixed(false);

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(unpaidExpense).post("/transactions").then().statusCode(200);

        // criar pendência (receita) não paga
        TransactionDTO unpaidIncome = new TransactionDTO();
        unpaidIncome.setName("Pending Income");
        unpaidIncome.setAmount(new BigDecimal("500.00"));
        unpaidIncome.setCategoryId(catFoodId);
        unpaidIncome.setAccountId(walletId);
        unpaidIncome.setType(TransactionType.RECEITA);
        unpaidIncome.setPaid(false);
        unpaidIncome.setDate(now);
        unpaidIncome.setIsFixed(false);

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(unpaidIncome).post("/transactions").then().statusCode(200);

        // chama full-summary
        given().header("Authorization", "Bearer " + token).param("start", start).param("end", end)
                .when().get("/dashboard/full-summary")
                .then().statusCode(200)
                .body("availableBalance", is(5650.0f))
                .body("projectedPayables", is(150.0f))
                .body("projectedVariables", is(166.67f))
                .body("projectedBalance", is(5833.33f))
                .body("pendingPayables.size()", is(1))
                .body("pendingReceivables.size()", is(1));
    }
}