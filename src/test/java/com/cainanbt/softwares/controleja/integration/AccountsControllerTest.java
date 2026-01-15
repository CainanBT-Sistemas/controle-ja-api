package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
public class AccountsControllerTest extends BaseTest {

    private String token;

    @BeforeEach
    void setupUser() {
        log.info("Stating AccountsControllerTest");
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("account_owner");
        user.setEmail("owner@bank.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email("owner@bank.com").password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

    }

    @Test
    @DisplayName("Deve criar uma conta (Wallet) com sucesso")
    void shouldCreateAccountSuccessfully() {
        AccountDTO account = new AccountDTO();
        account.setName("Carteira Principal");
        account.setType(AccountType.WALLET); // <--- MUDOU AQUI (Era String)
        account.setInitialBalance(new BigDecimal("150.00"));
        account.setInstitution("N/A");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(account)
                .when()
                .post("/accounts")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("currentBalance", is(150.0f));
    }

    @Test
    @DisplayName("Deve listar as contas do usuário")
    void shouldListAccounts() {
        AccountDTO account = new AccountDTO();
        account.setName("Conta Teste List");
        account.setType(AccountType.BANK); // <--- MUDOU AQUI
        account.setInitialBalance(BigDecimal.ZERO);
        account.setInstitution("Nubank");

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(account).post("/accounts");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/accounts")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].name", notNullValue());
    }
}
