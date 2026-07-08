package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        log.info("Starting AccountsControllerTest");
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("account_owner");
        user.setEmail("owner@bank.com");
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register");

        UserLoginDTO login = UserLoginDTO.builder().email("owner@bank.com").password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }

    @Test
    @DisplayName("Deve fazer o CRUD completo de Conta (Wallet) com sucesso")
    void shouldPerformFullAccountCRUD() {
        // 1. CREATE
        AccountDTO account = new AccountDTO();
        account.setName("Carteira Principal");
        account.setType(AccountType.WALLET);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setInstitution("N/A");
        account.setCalculateBalance(false);

        String accountId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("calculateBalance", is(false))
                .extract().path("id");

        // 2. READ (List)
        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts")
                .then().statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));

        // 3. READ (By ID)
        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts/" + accountId)
                .then().statusCode(200)
                .body("name", is("Carteira Principal"));

        // 4. UPDATE
        account.setName("Carteira Alterada");
        account.setCalculateBalance(true);
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().put("/accounts/" + accountId)
                .then().statusCode(200)
                .body("name", is("Carteira Alterada"))
                .body("calculateBalance", is(true));

        // 5. DELETE
        given().header("Authorization", "Bearer " + token)
                .when().delete("/accounts/" + accountId)
                .then().statusCode(200)
                .body("message", is("Registro excluído com sucesso."));
    }

    @Test
    @DisplayName("Não deve permitir consultar conta de outro usuário")
    void shouldNotAllowAccessToAccountFromAnotherUser() {
        AccountDTO account = new AccountDTO();
        account.setName("Conta Privada");
        account.setType(AccountType.BANK);
        account.setInitialBalance(new BigDecimal("300.00"));
        account.setInstitution("Banco");

        String accountId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(200)
                .extract().path("id");

        InsertUpdateUserDTO otherUser = new InsertUpdateUserDTO();
        otherUser.setUsername("account_intruder");
        otherUser.setEmail("intruder@bank.com");
        otherUser.setPassword("123456");
        given().contentType(ContentType.JSON).body(otherUser).post("/users/register");

        UserLoginDTO otherLogin = UserLoginDTO.builder().email("intruder@bank.com").password("123456").build();
        String otherToken = given().contentType(ContentType.JSON).body(otherLogin).post("/auth").then().extract().path("tokens.accessToken");

        given().header("Authorization", "Bearer " + otherToken)
                .when().get("/accounts/" + accountId)
                .then().statusCode(400)
                .body("message", is("Conta inválida (Não pertence ao usuário)."));
    }

    @Test
    @DisplayName("Não deve criar conta duplicada com mesmo nome e tipo")
    void shouldNotCreateDuplicatedAccountWithSameNameAndType() {
        AccountDTO account = new AccountDTO();
        account.setName("Conta Duplicada");
        account.setType(AccountType.WALLET);
        account.setInitialBalance(new BigDecimal("10.00"));
        account.setInstitution("");

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(400)
                .body("message", is("Este nome de conta já esta cadastrada"));
    }

    @Test
    @DisplayName("Nao deve excluir conta com saldo diferente de zero")
    void shouldBlockDeletingAccountWithNonZeroBalance() {
        AccountDTO account = new AccountDTO();
        account.setName("Conta com Saldo");
        account.setType(AccountType.BANK);
        account.setInitialBalance(new BigDecimal("50.00"));
        account.setInstitution("Banco");

        String accountId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(200)
                .extract().path("id");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/accounts/" + accountId)
                .then().statusCode(400)
                .body("message", is("Nao e possivel excluir esta conta porque existem lancamentos, saldo ou vinculos financeiros ativos. Resolva os vinculos antes de excluir."));

        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts/" + accountId)
                .then().statusCode(200)
                .body("id", is(accountId));
    }

    @Test
    @DisplayName("Nao deve excluir conta com lancamento ativo")
    void shouldBlockDeletingAccountWithActiveTransaction() {
        String accountId = createZeroBalanceAccount("Conta com Lancamento");
        String categoryId = createCategory("Despesa Conta");

        TransactionDTO transaction = new TransactionDTO();
        transaction.setName("Compra vinculada");
        transaction.setType(TransactionType.DESPESA);
        transaction.setAmount(new BigDecimal("20.00"));
        transaction.setDate(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
        transaction.setAccountId(java.util.UUID.fromString(accountId));
        transaction.setCategoryId(java.util.UUID.fromString(categoryId));
        transaction.setPaid(false);
        transaction.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(transaction)
                .when().post("/transactions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when().delete("/accounts/" + accountId)
                .then().statusCode(400)
                .body("message", is("Nao e possivel excluir esta conta porque existem lancamentos, saldo ou vinculos financeiros ativos. Resolva os vinculos antes de excluir."));

        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts/" + accountId)
                .then().statusCode(200)
                .body("id", is(accountId));
    }

    private String createZeroBalanceAccount(String name) {
        AccountDTO account = new AccountDTO();
        account.setName(name);
        account.setType(AccountType.WALLET);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setInstitution("");

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account)
                .when().post("/accounts")
                .then().statusCode(200)
                .extract().path("id");
    }

    private String createCategory(String name) {
        CategoryDTO category = new CategoryDTO();
        category.setName(name);
        category.setCategoryType("DESPESA");

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(category)
                .when().post("/categories")
                .then().statusCode(200)
                .extract().path("id");
    }
}
