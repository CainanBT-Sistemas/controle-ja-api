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
import com.cainanbt.softwares.controleja.enums.TransactionType;
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
    private UUID walletId;
    private UUID categoryId;

    @BeforeEach
    void setupUserAndData() {
        // 1. Cria Usuário Único (para isolamento)
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = "trans_" + unique + "@test.com";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("Tester " + unique);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        // 2. Loga e pega Token
        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        token = given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");

        // 3. Cria Categoria Padrão
        CategoryDTO cat = new CategoryDTO();
        cat.setName("Geral");
        cat.setCategoryType("DESPESA");
        categoryId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cat).post("/categories")
                .then().statusCode(200).extract().as(CategoryResponseDTO.class).getId();

        // 4. Cria Conta 'Carteira' com R$ 1.000,00
        AccountDTO acc = new AccountDTO();
        acc.setName("Minha Carteira");
        acc.setType(AccountType.WALLET);
        acc.setInitialBalance(new BigDecimal("1000.00"));
        walletId = given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(acc).post("/accounts")
                .then().statusCode(200).extract().as(AccountResponseDTO.class).getId();
    }

    @Test
    @DisplayName("CENÁRIO 1: Despesa em Conta Corrente (Deve debitar saldo)")
    void shouldCreateExpenseOnWallet() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Almoço");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("50.00"));
        dto.setDate(System.currentTimeMillis());
        dto.setPaid(true); // Pago na hora
        dto.setAccountId(walletId);
        dto.setCategoryId(categoryId);

        // 1. Cria Transação
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200);

        // 2. Valida Saldo (1000 - 50 = 950)
        given().header("Authorization", "Bearer " + token).get("/accounts")
                .then()
                .body("find { it.id == '" + walletId + "' }.currentBalance", is(950.0f));
    }

    @Test
    @DisplayName("CENÁRIO 2: Receita em Conta Corrente (Deve aumentar saldo)")
    void shouldCreateIncomeOnWallet() {
        TransactionDTO dto = new TransactionDTO();
        dto.setName("Venda Extra");
        dto.setType(TransactionType.RECEITA);
        dto.setAmount(new BigDecimal("200.00"));
        dto.setDate(System.currentTimeMillis());
        dto.setPaid(true);
        dto.setAccountId(walletId);
        dto.setCategoryId(categoryId);

        // 1. Cria Transação
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200);

        // 2. Valida Saldo (1000 + 200 = 1200)
        given().header("Authorization", "Bearer " + token).get("/accounts")
                .then()
                .body("find { it.id == '" + walletId + "' }.currentBalance", is(1200.0f));
    }

    @Test
    @DisplayName("CENÁRIO 3: Compra Parcelada no Crédito (Limite Global vs Parcelas)")
    void shouldCreateInstallmentsOnCreditCard() {
        // 1. Cria Cartão com Limite 2000
        UUID cardAccountId = createCreditCardAux("Nubank Gold", new BigDecimal("2000.00"));

        // 2. Cria Despesa de R$ 900 em 3x
        TransactionDTO dto = new TransactionDTO();
        dto.setName("TV Smart");
        dto.setType(TransactionType.DESPESA);
        dto.setAmount(new BigDecimal("900.00")); // Valor Total
        dto.setDate(System.currentTimeMillis());
        dto.setPaid(false); // Fatura aberta
        dto.setAccountId(cardAccountId);
        dto.setCategoryId(categoryId);
        dto.setInstallments(3);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto).post("/transactions")
                .then().statusCode(200);

        // 3. Validações
        // A) Limite deve cair pelo TOTAL (2000 - 900 = 1100)
        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Nubank Gold' }.currentLimit", is(1100.0f));

        // B) Devem existir 3 transações
        given().header("Authorization", "Bearer " + token).get("/transactions")
                .then().body("size()", is(3)); // TV Smart (1/3), (2/3), (3/3)

        // C) Saldo da conta do cartão deve refletir a dívida (-300 de cada parcela somada ou -900 total dependendo da impl)
        // No nosso impl atual, o saldo devedor vai diminuindo a cada parcela criada.
        // Como o teste roda rápido, checamos se o saldo está negativo
        given().header("Authorization", "Bearer " + token).get("/accounts")
                .then().body("find { it.id == '" + cardAccountId + "' }.currentBalance", is(-900.0f));
    }

    @Test
    @DisplayName("CENÁRIO 4: Pagamento de Fatura (Transferência + Restauração de Limite)")
    void shouldPayInvoiceAndRestoreLimit() {
        // 1. Cria Cartão com Limite 500
        UUID cardAccountId = createCreditCardAux("Cartão Devedor", new BigDecimal("500.00"));

        // 2. Gasta R$ 300 no cartão (Limite cai para 200)
        TransactionDTO gasto = new TransactionDTO();
        gasto.setName("Gasto");
        gasto.setType(TransactionType.DESPESA);
        gasto.setAmount(new BigDecimal("300.00"));
        gasto.setDate(System.currentTimeMillis());
        gasto.setPaid(false);
        gasto.setAccountId(cardAccountId);
        gasto.setCategoryId(categoryId);
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON).body(gasto).post("/transactions");

        // 3. Paga a Fatura (Transfere R$ 300 da Carteira -> Cartão)
        TransactionDTO pagamento = new TransactionDTO();
        pagamento.setName("Pagamento Fatura");
        pagamento.setType(TransactionType.PAGAMENTO_FATURA);
        pagamento.setAmount(new BigDecimal("300.00"));
        pagamento.setDate(System.currentTimeMillis());
        pagamento.setPaid(true);
        pagamento.setAccountId(walletId); // Sai da Carteira (1000)
        pagamento.setTargetAccountId(cardAccountId); // Vai para o Cartão
        pagamento.setCategoryId(categoryId);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(pagamento).post("/transactions")
                .then().statusCode(200);

        // 4. Valida Limite Restaurado (Voltou para 500)
        given().header("Authorization", "Bearer " + token).get("/cards")
                .then().body("find { it.name == 'Cartão Devedor' }.currentLimit", is(500.0f));

        // 5. Valida Saldo da Carteira (1000 - 300 = 700)
        given().header("Authorization", "Bearer " + token).get("/accounts")
                .then().body("find { it.id == '" + walletId + "' }.currentBalance", is(700.0f));
    }

    // Helper para criar cartão e retornar o ID da Conta Vinculada
    private UUID createCreditCardAux(String name, BigDecimal limit) {
        CreditCardDTO cardDto = new CreditCardDTO();
        cardDto.setName(name);
        cardDto.setLimit(limit);
        cardDto.setCloseDay(10);
        cardDto.setBestDay(15);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(cardDto).post("/cards")
                .then().statusCode(200);

        String idStr = given().header("Authorization", "Bearer " + token).get("/accounts")
                .then().extract().path("find { it.name == '" + name + " (Fatura)' }.id");
        return UUID.fromString(idStr);
    }
}