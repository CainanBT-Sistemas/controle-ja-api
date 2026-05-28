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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

        String transferOutId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("parentTransactionId", nullValue())
                .extract().path("id");

        // Valida saldos cruzados
        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(800.0f)); // 1000 - 200

        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(200.0f)); // 0 + 200

        given().header("Authorization", "Bearer " + token)
                .param("start", DateUtils.getEpochNow() - 86400000L)
                .param("end", DateUtils.getEpochNow() + 86400000L)
                .get("/transactions")
                .then().statusCode(200)
                .body("find { it.type == 'TRANSFERENCIA_SAIDA' }.id", is(transferOutId))
                .body("find { it.type == 'TRANSFERENCIA_SAIDA' }.parentTransactionId", nullValue())
                .body("find { it.type == 'TRANSFERENCIA_ENTRADA' }.parentTransactionId", is(transferOutId));
    }

    @Test
    @DisplayName("CENÁRIO 2.1: Editar transferência pela saída atualiza os dois lados")
    void shouldUpdateTransferPairFromOutgoingTransaction() {
        String transferOutId = createTransfer("Reserva", new BigDecimal("200.00"));

        TransactionDTO update = transferUpdateDto("Reserva editada", new BigDecimal("300.00"), walletId, bankId, true);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(update)
                .put("/transactions/" + transferOutId)
                .then().statusCode(200)
                .body("id", is(transferOutId))
                .body("type", is("TRANSFERENCIA_SAIDA"))
                .body("amount", is(300.0f))
                .body("parentTransactionId", nullValue());

        List<Map<String, Object>> transfers = listTransfersByName("Reserva editada");

        Map<String, Object> out = findByType(transfers, "TRANSFERENCIA_SAIDA");
        Map<String, Object> in = findByType(transfers, "TRANSFERENCIA_ENTRADA");

        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, out.get("id"));
        org.junit.jupiter.api.Assertions.assertNull(out.get("parentTransactionId"));
        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, in.get("parentTransactionId"));
        org.junit.jupiter.api.Assertions.assertEquals(300.0f, ((Number) out.get("amount")).floatValue());
        org.junit.jupiter.api.Assertions.assertEquals(300.0f, ((Number) in.get("amount")).floatValue());

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(700.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(300.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2.2: Editar transferência pela entrada encontra a saída e atualiza os dois lados")
    void shouldUpdateTransferPairFromIncomingTransaction() {
        String transferOutId = createTransfer("Aporte", new BigDecimal("200.00"));
        String transferInId = findIncomingIdForParent(transferOutId);

        TransactionDTO update = transferUpdateDto("Aporte editado", new BigDecimal("150.00"), walletId, bankId, false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(update)
                .put("/transactions/" + transferInId)
                .then().statusCode(200)
                .body("id", is(transferOutId))
                .body("type", is("TRANSFERENCIA_SAIDA"))
                .body("amount", is(150.0f));

        List<Map<String, Object>> transfers = listTransfersByName("Aporte editado");
        Map<String, Object> out = findByType(transfers, "TRANSFERENCIA_SAIDA");
        Map<String, Object> in = findByType(transfers, "TRANSFERENCIA_ENTRADA");

        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, out.get("paid"));
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, in.get("paid"));
        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, in.get("parentTransactionId"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(1000.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(0.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2.2.1: Inverter origem e destino atualiza os dois lados da transferência")
    void shouldSwapTransferOriginAndDestinationAccounts() {
        String transferOutId = createTransfer("Swap contas", new BigDecimal("200.00"));

        TransactionDTO update = transferUpdateDto("Swap contas", new BigDecimal("200.00"), bankId, walletId, true);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(update)
                .put("/transactions/" + transferOutId)
                .then().statusCode(200)
                .body("id", is(transferOutId))
                .body("type", is("TRANSFERENCIA_SAIDA"))
                .body("accountId", is(bankId.toString()))
                .body("parentTransactionId", nullValue());

        List<Map<String, Object>> transfers = listTransfersByName("Swap contas");
        Map<String, Object> out = findByType(transfers, "TRANSFERENCIA_SAIDA");
        Map<String, Object> in = findByType(transfers, "TRANSFERENCIA_ENTRADA");

        org.junit.jupiter.api.Assertions.assertEquals(bankId.toString(), out.get("accountId"));
        org.junit.jupiter.api.Assertions.assertEquals(walletId.toString(), in.get("accountId"));
        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, in.get("parentTransactionId"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(1200.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(-200.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2.3: Excluir transferência por qualquer lado remove o par")
    void shouldDeleteTransferPairFromEitherSide() {
        String firstTransferOutId = createTransfer("Excluir pela saída", new BigDecimal("100.00"));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + firstTransferOutId)
                .then().statusCode(200);

        assertNoTransfersByName("Excluir pela saída");

        String secondTransferOutId = createTransfer("Excluir pela entrada", new BigDecimal("120.00"));
        String secondTransferInId = findIncomingIdForParent(secondTransferOutId);

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + secondTransferInId)
                .then().statusCode(200);

        assertNoTransfersByName("Excluir pela entrada");
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
        cardDto.setTotalLimit(limit);
        cardDto.setCloseDay(10);
        cardDto.setBestDay(15);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cardDto).post("/cards")
                .then().statusCode(200);

        String idStr = given().header("Authorization", "Bearer " + token).get("/cards")
                .then().extract().path("find { it.name == '" + name + "' }.accountId");

        return UUID.fromString(idStr);
    }

    private String createTransfer(String name, BigDecimal amount) {
        TransactionDTO dto = transferUpdateDto(name, amount, walletId, bankId, true);

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("id", notNullValue())
                .body("parentTransactionId", nullValue())
                .extract().path("id");
    }

    private TransactionDTO transferUpdateDto(String name, BigDecimal amount, UUID originAccountId, UUID targetAccountId, boolean paid) {
        TransactionDTO dto = new TransactionDTO();
        dto.setName(name);
        dto.setType(TransactionType.TRANSFERENCIA);
        dto.setAmount(amount);
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(paid);
        dto.setAccountId(originAccountId);
        dto.setTargetAccountId(targetAccountId);
        dto.setCategoryId(categoryId);
        dto.setIsFixed(false);
        return dto;
    }

    private List<Map<String, Object>> listTransfersByName(String name) {
        return given().header("Authorization", "Bearer " + token)
                .param("start", DateUtils.getEpochNow() - 86400000L)
                .param("end", DateUtils.getEpochNow() + 86400000L)
                .get("/transactions")
                .then().statusCode(200)
                .extract().jsonPath().getList("findAll { it.name == '" + name + "' }");
    }

    private String findIncomingIdForParent(String parentId) {
        return given().header("Authorization", "Bearer " + token)
                .param("start", DateUtils.getEpochNow() - 86400000L)
                .param("end", DateUtils.getEpochNow() + 86400000L)
                .get("/transactions")
                .then().statusCode(200)
                .extract().path("find { it.parentTransactionId == '" + parentId + "' }.id");
    }

    private Map<String, Object> findByType(List<Map<String, Object>> transfers, String type) {
        return transfers.stream()
                .filter(tx -> type.equals(tx.get("type")))
                .findFirst()
                .orElseThrow();
    }

    private void assertNoTransfersByName(String name) {
        given().header("Authorization", "Bearer " + token)
                .param("start", DateUtils.getEpochNow() - 86400000L)
                .param("end", DateUtils.getEpochNow() + 86400000L)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name == '" + name + "' }", hasSize(0));
    }
}
