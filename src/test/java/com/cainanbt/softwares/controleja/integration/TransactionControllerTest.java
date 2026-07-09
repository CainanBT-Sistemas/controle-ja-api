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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionControllerTest extends BaseTest {

    @Autowired
    private RecurrenceWorkerService workerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        acc1.setName("Carteira Teste " + unique);
        acc1.setType(AccountType.WALLET);
        acc1.setInitialBalance(new BigDecimal("1000.00"));
        walletId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc1).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();

        AccountDTO acc2 = new AccountDTO();
        acc2.setName("Conta Itau " + unique);
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
        dto.setIsFixed(false);

        io.restassured.response.Response transferResponse = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200).body("parentTransactionId", nullValue())
                .extract().response();
        String transferOutId = transferResponse.path("id");
        String transferCategoryId = transferResponse.path("categoryId");
        org.junit.jupiter.api.Assertions.assertNotNull(transferCategoryId);
        assertEquals(
                "TRANSFERENCIA",
                jdbcTemplate.queryForObject(
                        "SELECT category_type FROM category WHERE id = ? AND is_default = true",
                        String.class,
                        UUID.fromString(transferCategoryId)
                )
        );

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
                .body("find { it.type == 'TRANSFERENCIA_SAIDA' }.categoryId", is(transferCategoryId))
                .body("find { it.type == 'TRANSFERENCIA_SAIDA' }.parentTransactionId", nullValue())
                .body("find { it.type == 'TRANSFERENCIA_ENTRADA' }.categoryId", is(transferCategoryId))
                .body("find { it.type == 'TRANSFERENCIA_ENTRADA' }.parentTransactionId", is(transferOutId));
    }

    @Test
    @DisplayName("Transferência resolve categoria técnica sem depender do payload")
    void shouldResolveTechnicalTransferCategoryWithoutPayloadCategory() {
        TransactionDTO dto = transferUpdateDto(
                "Transferência técnica",
                new BigDecimal("125.00"),
                walletId,
                bankId,
                true
        );

        String transferOutId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .extract().path("id");

        List<Map<String, Object>> transfers = listTransfersByName("Transferência técnica");
        Map<String, Object> out = findByType(transfers, "TRANSFERENCIA_SAIDA");
        Map<String, Object> in = findByType(transfers, "TRANSFERENCIA_ENTRADA");

        org.junit.jupiter.api.Assertions.assertNotNull(out.get("categoryId"));
        assertEquals(out.get("categoryId"), in.get("categoryId"));
        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, in.get("parentTransactionId"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(875.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(125.0f));
    }

    @Test
    @DisplayName("Transferência recorrente preserva categoria técnica na regra e projeções")
    void shouldPreserveTechnicalCategoryInRecurringTransferProjections() {
        TransactionDTO dto = transferUpdateDto(
                "Transferência recorrente técnica",
                new BigDecimal("25.00"),
                walletId,
                bankId,
                false
        );
        dto.setIsFixed(true);
        dto.setRecurrenceFrequency(com.cainanbt.softwares.controleja.enums.RecurrenceFrequency.MONTHLY);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("categoryId", org.hamcrest.Matchers.notNullValue());

        Integer transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t "
                        + "JOIN category c ON c.id = t.category_id "
                        + "WHERE t.name = ? "
                        + "AND t.type IN ('TRANSFERENCIA_SAIDA', 'TRANSFERENCIA_ENTRADA') "
                        + "AND c.category_type = 'TRANSFERENCIA' "
                        + "AND c.is_default = true",
                Integer.class,
                "Transferência recorrente técnica"
        );
        org.junit.jupiter.api.Assertions.assertTrue(transferCount != null && transferCount > 2);
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM recurrence_rules r "
                                + "JOIN category c ON c.id = r.category_id "
                                + "WHERE r.name = ? AND c.category_type = 'TRANSFERENCIA' "
                                + "AND c.is_default = true",
                        Integer.class,
                        "Transferência recorrente técnica"
                )
        );
    }

    @Test
    @DisplayName("Transferência sem categoria técnica retorna erro de domínio e não altera saldos")
    void shouldRejectTransferWhenTechnicalCategoryIsMissing() {
        assertEquals(
                "NO",
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'transactions' "
                                + "AND column_name = 'category_id'",
                        String.class
                )
        );
        jdbcTemplate.update(
                "UPDATE category SET deleted_at = ? "
                        + "WHERE category_type = 'TRANSFERENCIA' AND is_default = true",
                DateUtils.getEpochNow()
        );

        TransactionDTO dto = transferUpdateDto(
                "Transferência sem categoria técnica",
                new BigDecimal("50.00"),
                walletId,
                bankId,
                true
        );

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(400)
                .body("message", containsString("categoria tecnica de transferencia"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(1000.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(0.0f));
    }

    @Test
    @DisplayName("Transferência bloqueia origem igual ao destino sem depender de categoria")
    void shouldRejectTransferBetweenSameAccountWithoutCategory() {
        TransactionDTO dto = transferUpdateDto(
                "Transferência inválida",
                new BigDecimal("50.00"),
                walletId,
                walletId,
                true
        );

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(400)
                .body("message", containsString("origem e destino devem ser diferentes"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(1000.0f));
    }

    @Test
    @DisplayName("Despesa continua exigindo categoria")
    void shouldRejectExpenseWithoutCategory() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Despesa sem categoria");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("50.00"));
        dto.setDate(DateUtils.getEpochNow());
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setIsFixed(false);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(400)
                .body("message", containsString("Categoria não encontrada"));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + walletId)
                .then().body("currentBalance", is(1000.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2.0.1: Transferência para poupança deve ser permitida mesmo fora do cálculo de saldo")
    void shouldPerformTransferToSavingsAccountEvenWhenItDoesNotCalculateBalance() {
        UUID savingsId = createAccount("Poupança Inter", AccountType.SAVINGS, BigDecimal.ZERO, false);
        TransactionDTO dto = transferUpdateDto("Aplicação Poupança", new BigDecimal("1000.00"), bankId, savingsId, true);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200)
                .body("type", is("TRANSFERENCIA_SAIDA"))
                .body("accountId", is(bankId.toString()));

        given().header("Authorization", "Bearer " + token).get("/accounts/" + bankId)
                .then().body("currentBalance", is(-1000.0f));
        given().header("Authorization", "Bearer " + token).get("/accounts/" + savingsId)
                .then().body("currentBalance", is(1000.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2.0.2: Transferência para investimento patrimonial deve ser recusada")
    void shouldRejectTransferToInvestmentAccount() {
        UUID patrimonyId = createAccount("Investimento Patrimonial", AccountType.INVESTMENT, BigDecimal.ZERO, false);
        TransactionDTO dto = transferUpdateDto("Aplicação Investimento", new BigDecimal("500.00"), bankId, patrimonyId, true);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(400)
                .body("message", containsString("Transferencia permitida apenas entre Carteira, Conta Bancaria e Poupanca"));
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
                .body("categoryId", org.hamcrest.Matchers.notNullValue())
                .body("parentTransactionId", nullValue());

        List<Map<String, Object>> transfers = listTransfersByName("Reserva editada");

        Map<String, Object> out = findByType(transfers, "TRANSFERENCIA_SAIDA");
        Map<String, Object> in = findByType(transfers, "TRANSFERENCIA_ENTRADA");

        org.junit.jupiter.api.Assertions.assertEquals(transferOutId, out.get("id"));
        org.junit.jupiter.api.Assertions.assertNotNull(out.get("categoryId"));
        assertEquals(out.get("categoryId"), in.get("categoryId"));
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
    @DisplayName("Compra à vista aberta pode ser convertida em 10 parcelas sem nova baixa de limite")
    void shouldConvertOpenCashCreditCardPurchaseToTenInstallments() {
        String cardName = "Cartão Conversão " + System.nanoTime();
        UUID cardAccountId = createCreditCardAux(cardName, new BigDecimal("2000.00"));
        String cardId = given().header("Authorization", "Bearer " + token)
                .get("/cards")
                .then().statusCode(200)
                .extract().path("find { it.name == '" + cardName + "' }.id");

        TransactionDTO purchase = new TransactionDTO();
        purchase.setName("Notebook");
        purchase.setType(TransactionType.DESPESA);
        purchase.setAmount(new BigDecimal("1500.00"));
        purchase.setDate(DateUtils.getEpochNow());
        purchase.setPaid(false);
        purchase.setAccountId(cardAccountId);
        purchase.setCreditCardId(UUID.fromString(cardId));
        purchase.setCategoryId(categoryId);
        purchase.setInstallments(1);
        purchase.setIsFixed(false);

        String purchaseId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(purchase).post("/transactions")
                .then().statusCode(200)
                .extract().path("id");

        BigDecimal limitBefore = jdbcTemplate.queryForObject(
                "SELECT current_limit FROM credit_cards WHERE id = ?",
                BigDecimal.class,
                UUID.fromString(cardId)
        );

        purchase.setInstallments(10);
        given().header("Authorization", "Bearer " + token)
                .queryParam("operationScope", "ALL")
                .contentType(ContentType.JSON).body(purchase)
                .put("/transactions/" + purchaseId)
                .then().statusCode(200)
                .body("id", is(purchaseId));

        assertEquals(
                10,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM installment_plan WHERE purchase_id = ? AND deleted_at IS NULL",
                        Integer.class,
                        UUID.fromString(purchaseId)
                )
        );
        assertEquals(
                new BigDecimal("1500.00"),
                jdbcTemplate.queryForObject(
                        "SELECT SUM(amount) FROM installment_plan WHERE purchase_id = ? AND deleted_at IS NULL",
                        BigDecimal.class,
                        UUID.fromString(purchaseId)
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM transactions WHERE id = ? AND deleted_at IS NULL",
                        Integer.class,
                        UUID.fromString(purchaseId)
                )
        );
        assertEquals(
                limitBefore,
                jdbcTemplate.queryForObject(
                        "SELECT current_limit FROM credit_cards WHERE id = ?",
                        BigDecimal.class,
                        UUID.fromString(cardId)
                )
        );
    }

    @Test
    @DisplayName("Adiantamento e desconto de outra compra na mesma fatura não bloqueiam conversão")
    void shouldConvertPurchaseWhenAnotherPurchaseInInvoiceWasAdvancedWithDiscount() {
        String cardName = "Cartão Conversão Isolada " + System.nanoTime();
        UUID cardAccountId = createCreditCardAux(cardName, new BigDecimal("3000.00"));
        UUID cardId = UUID.fromString(given().header("Authorization", "Bearer " + token)
                .get("/cards")
                .then().statusCode(200)
                .extract().path("find { it.name == '" + cardName + "' }.id"));

        TransactionDTO targetPurchase = new TransactionDTO();
        targetPurchase.setName("Compra para converter");
        targetPurchase.setType(TransactionType.DESPESA);
        targetPurchase.setAmount(new BigDecimal("300.00"));
        targetPurchase.setDate(DateUtils.getEpochNow());
        targetPurchase.setPaid(false);
        targetPurchase.setAccountId(cardAccountId);
        targetPurchase.setCreditCardId(cardId);
        targetPurchase.setCategoryId(categoryId);
        targetPurchase.setInstallments(1);
        targetPurchase.setIsFixed(false);

        UUID targetPurchaseId = UUID.fromString(given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(targetPurchase).post("/transactions")
                .then().statusCode(200)
                .extract().path("id"));

        TransactionDTO otherPurchase = new TransactionDTO();
        otherPurchase.setName("Outra compra adiantada");
        otherPurchase.setType(TransactionType.DESPESA);
        otherPurchase.setAmount(new BigDecimal("600.00"));
        otherPurchase.setDate(targetPurchase.getDate());
        otherPurchase.setPaid(false);
        otherPurchase.setAccountId(cardAccountId);
        otherPurchase.setCreditCardId(cardId);
        otherPurchase.setCategoryId(categoryId);
        otherPurchase.setInstallments(3);
        otherPurchase.setIsFixed(false);

        UUID otherPurchaseId = UUID.fromString(given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(otherPurchase).post("/transactions")
                .then().statusCode(200)
                .extract().path("id"));

        UUID currentInvoiceId = jdbcTemplate.queryForObject(
                "SELECT invoices_id FROM installment_plan "
                        + "WHERE purchase_id = ? AND current_installment = 1 AND deleted_at IS NULL",
                UUID.class,
                targetPurchaseId
        );

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "purchaseId", otherPurchaseId,
                        "quantityToAdvance", 1,
                        "discountAmount", new BigDecimal("10.00")
                ))
                .post("/invoices/" + currentInvoiceId + "/advance")
                .then().statusCode(200);

        targetPurchase.setInstallments(3);
        given().header("Authorization", "Bearer " + token)
                .queryParam("operationScope", "ALL")
                .contentType(ContentType.JSON).body(targetPurchase)
                .put("/transactions/" + targetPurchaseId)
                .then().statusCode(200)
                .body("id", is(targetPurchaseId.toString()));

        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM installment_plan "
                                + "WHERE purchase_id = ? AND type = 'DESPESA' AND deleted_at IS NULL",
                        Integer.class,
                        targetPurchaseId
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM installment_plan "
                                + "WHERE purchase_id = ? AND type = 'RECEITA' "
                                + "AND amount = -10.00 AND deleted_at IS NULL",
                        Integer.class,
                        otherPurchaseId
                )
        );
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
                .put("/transactions/" + txId + "?operationScope=FROM_THIS_FORWARD")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .param("start", start).param("end", end)
                .get("/transactions")
                .then().statusCode(200)
                .body("findAll { it.name == 'Spotify Premium' && it.amount == 25.0f }", hasSize(greaterThanOrEqualTo(12)));

        given().header("Authorization", "Bearer " + token)
                .delete("/transactions/" + txId + "?operationScope=FROM_THIS_FORWARD")
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
                .delete("/transactions/" + primeiraParcelaId + "?operationScope=FROM_THIS_FORWARD")
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

    private UUID createAccount(String name, AccountType type, BigDecimal initialBalance, boolean calculateBalance) {
        AccountDTO account = new AccountDTO();
        account.setName(name + " " + System.nanoTime());
        account.setType(type);
        account.setInitialBalance(initialBalance);
        account.setCalculateBalance(calculateBalance);

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(account).post("/accounts")
                .then().statusCode(200)
                .extract().as(AccountResponseDTO.class).getId();
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
