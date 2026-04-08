package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountResponseDTO;
import com.cainanbt.softwares.controleja.dtos.responses.CategoryResponseDTO;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.enums.RecurrenceFrequency;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.workers.RecurrenceWorkerService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class TransactionControllerTest extends BaseTest {

    @Autowired
    private RecurrenceWorkerService workerService;

    private String token;
    private UUID walletId;
    private UUID bankId;
    private UUID categoryId;

    @BeforeEach
    void setupUserAndData() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "trans_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Tester " + unique);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        CategoryDTO cat = new CategoryDTO();
        cat.setName("Geral");
        cat.setCategoryType("DESPESA");
        categoryId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cat).post("/categories")
                .then().statusCode(200).extract().as(CategoryResponseDTO.class).getId();

        AccountDTO acc1 = new AccountDTO();
        acc1.setName("Minha Carteira");
        acc1.setType(AccountType.WALLET);
        acc1.setInitialBalance(new BigDecimal("1000.00"));
        walletId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc1).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();

        AccountDTO acc2 = new AccountDTO();
        acc2.setName("Conta Itaú");
        acc2.setType(AccountType.BANK);
        acc2.setInitialBalance(new BigDecimal("0.00"));
        bankId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc2).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();
    }

    @Test
    @DisplayName("CENÁRIO 1: Despesa e Receita (CRUD Básico)")
    void shouldCreateUpdateAndDeleteTransaction() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Almoço");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("50.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setCategoryId(categoryId);
        dto.setIsFixed(false);

        String txId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("id", notNullValue())
                .extract().path("id");

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(950.0f));

        dto.setAmount(new BigDecimal("70.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).put("/transactions/" + txId)
                .then().statusCode(200)
                .body("amount", is(70.0f));

        // Listagem
        given().header("Authorization", "Bearer " + token)
                .param("start", DateUtils.getEpochNow() - 86400000L)
                .param("end", DateUtils.getEpochNow() + 86400000L)
                .get("/transactions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token).delete("/transactions/" + txId)
                .then().statusCode(200);
    }

    @Test
    @DisplayName("CENÁRIO 2: Transferência entre Contas (Partidas Dobradas)")
    void shouldPerformTransferBetweenAccounts() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Enviando pro Banco");
        dto.setType(TransactionType.TRANSFERENCIA);
        dto.setAmount(new BigDecimal("200.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true);
        dto.setAccountId(walletId); // Sai daqui
        dto.setTargetAccountId(bankId); // Entra aqui
        dto.setCategoryId(categoryId);
        dto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200);

        // Valida saldos cruzados
        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(800.0f)); // 1000 - 200

        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(200.0f)); // 0 + 200
    }

    @Test
    @DisplayName("CENÁRIO 3: Compra Parcelada no Crédito e Pagamento de Fatura")
    void shouldCreateInstallmentsOnCreditCardAndPay() {
        UUID cardAccountId = createCreditCardAux("Nubank Gold", new BigDecimal("2000.00"));

        TransactionDTO dto = new TransactionDTO();
        dto.setName("TV Smart");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("900.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(false);
        dto.setAccountId(cardAccountId);
        dto.setCategoryId(categoryId);
        dto.setInstallments(3);
        dto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Nubank Gold' }.currentLimit", is(1100.0f));

        TransactionDTO pagamento = new TransactionDTO();
        pagamento.setName("Pagamento Fatura");
        pagamento.setType(TransactionType.PAGAMENTO_FATURA);
        pagamento.setAmount(new BigDecimal("300.00"));
        pagamento.setDate(DateUtils.getEpochNow());
        pagamento.setPaid(true);
        pagamento.setAccountId(walletId);
        pagamento.setTargetAccountId(cardAccountId);
        pagamento.setCategoryId(categoryId);
        pagamento.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(pagamento).post("/transactions")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Nubank Gold' }.currentLimit", is(1400.0f));
    }

    @Test
    @DisplayName("CENÁRIO 4: Efeito Cascata (Update) e Cancelamento (Delete) de Assinatura")
    void shouldCascadeUpdateAndDeleteFixedTransaction() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Spotify Premium");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("20.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setCategoryId(categoryId);
        dto.setIsFixed(true);
        dto.setRecurrenceFrequency(RecurrenceFrequency.MONTHLY);

        String txId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("id", notNullValue())
                .extract().path("id");

        workerService.processProjections();

        long start = DateUtils.getEpochNow() - 86400000L;
        long end = DateUtils.getEpochNow() + (366L * 86400000L); // +1 ano

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name == 'Spotify Premium' }", hasSize(greaterThanOrEqualTo(12)));

        dto.setAmount(new BigDecimal("25.00"));
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .put("/transactions/" + txId + "?updateFuture=true")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name == 'Spotify Premium' && it.amount == 25.0f }", hasSize(greaterThanOrEqualTo(12)));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + txId + "?cancelFuture=true")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name == 'Spotify Premium' }", hasSize(0));
    }

    @Test
    @DisplayName("CENÁRIO 5: Financiamento/Carnê (Geração Imediata e Exclusão em Cascata)")
    void shouldCreateAndCascadeDeleteInstallmentsForBank() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Financiamento Moto");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true); // Paga a primeira
        dto.setAccountId(bankId);
        dto.setCategoryId(categoryId);
        dto.setIsFixed(false);
        dto.setInstallments(3);

        String primeiraParcelaId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("amount", is(333.34f))
                .extract().path("id");

        long start = DateUtils.getEpochNow() - 86400000L;
        long end = DateUtils.getEpochNow() + (100L * 86400000L); // +3 meses

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name.contains('Financiamento Moto') }", hasSize(3));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + primeiraParcelaId + "?cancelFuture=true")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name.contains('Financiamento Moto') }", hasSize(0));
    }

    @Test
    @DisplayName("CENÁRIO 6: Exclusão de Compra no Cartão de Crédito (Restaurar Limite)")
    void shouldDeleteCreditCardTransactionAndRestoreLimit() {
        // 1. Cria o Cartão com 5000 de limite
        UUID cardAccountId = createCreditCardAux("Itaú Black", new BigDecimal("5000.00"));

        // 2. Faz uma compra de 3000 em 10 vezes
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Geladeira Inteligente");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("3000.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(false);
        dto.setAccountId(cardAccountId);
        dto.setCategoryId(categoryId);
        dto.setInstallments(10);
        dto.setIsFixed(false);

        String txId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .extract().path("id");

        // Valida que o limite caiu para 2000 (5000 - 3000)
        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Itaú Black' }.currentLimit", is(2000.0f));

        // 3. O usuário se arrepende e exclui a transação
        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + txId)
                .then().statusCode(200);

        // 4. Valida que o limite voltou para 5000 magicamente!
        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Itaú Black' }.currentLimit", is(5000.0f));
    }

    private UUID createCreditCardAux(String name, BigDecimal limit) {
        CreditCardDTO cardDto = new CreditCardDTO();
        cardDto.setName(name);
        cardDto.setLimit(limit);
        cardDto.setCloseDay(10);
        cardDto.setBestDay(15);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cardDto).post("/cards")
                .then().statusCode(200);

        String idStr = given().header("Authorization", "Bearer " + token).get("/cards")
                .then().extract().path("find { it.name == '" + name + "' }.accountId");

        return UUID.fromString(idStr);
    }
}