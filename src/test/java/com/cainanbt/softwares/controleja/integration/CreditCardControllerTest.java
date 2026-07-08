package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.TransactionDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        dto.setTotalLimit(new BigDecimal("5000.00"));
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
        dto.setTotalLimit(new BigDecimal("10000.00"));
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
        dto.setTotalLimit(BigDecimal.TEN);
        dto.setCloseDay(1);
        dto.setBestDay(10);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(dto)
                .when().post("/cards")
                .then().statusCode(400)
                .body("title", is("Limite Atingido"));
    }

    @Test
    @DisplayName("Deve bloquear acesso a cartão de outro usuário")
    void shouldBlockAccessToAnotherUsersCard() {
        String firstUserCardId = createCardAux("Card Privado");
        String secondUserToken = registerAndLoginUser();

        CreditCardDTO updateDto = new CreditCardDTO();
        updateDto.setName("Tentativa Indevida");
        updateDto.setTotalLimit(new BigDecimal("2000.00"));
        updateDto.setCloseDay(5);
        updateDto.setBestDay(12);

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().get("/cards/" + firstUserCardId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .contentType(ContentType.JSON)
                .body(updateDto)
                .when().put("/cards/" + firstUserCardId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));

        given().header("Authorization", "Bearer " + secondUserToken)
                .when().delete("/cards/" + firstUserCardId)
                .then().statusCode(400)
                .body("title", is("Acesso negado"));
    }

    @Test
    @DisplayName("Nao deve excluir cartao com lancamento e fatura vinculados")
    void shouldBlockDeletingCardWithFinancialLinks() {
        String cardAccountId = createCardAccountAux("Card com Vinculo");
        String categoryId = createCategory("Despesa Cartao");

        TransactionDTO transaction = new TransactionDTO();
        transaction.setName("Compra no cartao");
        transaction.setType(TransactionType.DESPESA);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setDate(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
        transaction.setAccountId(UUID.fromString(cardAccountId));
        transaction.setCategoryId(UUID.fromString(categoryId));
        transaction.setPaid(false);
        transaction.setIsFixed(false);
        transaction.setInstallments(1);

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(transaction)
                .when().post("/transactions")
                .then().statusCode(200);

        String cardId = given().header("Authorization", "Bearer " + token)
                .when().get("/cards")
                .then().statusCode(200)
                .extract().path("[0].id");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/cards/" + cardId)
                .then().statusCode(400)
                .body("message", is("Nao e possivel excluir este cartao porque existem faturas, parcelas ou lancamentos vinculados. Quite, cancele ou ajuste os lancamentos antes de excluir."));

        given().header("Authorization", "Bearer " + token)
                .when().get("/cards/" + cardId)
                .then().statusCode(200)
                .body("id", is(cardId));
    }

    @Test
    @DisplayName("Nao deve excluir diretamente a conta espelho de cartao ativo")
    void shouldBlockDeletingCreditCardMirrorAccount() {
        String cardAccountId = createCardAccountAux("Card com Conta Espelho");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/accounts/" + cardAccountId)
                .then().statusCode(400)
                .body("message", is("Nao e possivel excluir esta conta porque existem lancamentos, saldo ou vinculos financeiros ativos. Resolva os vinculos antes de excluir."));

        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts/" + cardAccountId)
                .then().statusCode(200)
                .body("id", is(cardAccountId));
    }

    private String createCardAux(String name) {
        return createCard(name, "id");
    }

    private String createCardAccountAux(String name) {
        return createCard(name, "accountId");
    }

    private String createCard(String name, String responseField) {
        CreditCardDTO dto = new CreditCardDTO();
        dto.setName(name);
        dto.setTotalLimit(new BigDecimal("1000"));
        dto.setCloseDay(1);
        dto.setBestDay(10);

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(dto)
                .when().post("/cards")
                .then().statusCode(200)
                .extract().path(responseField);
    }

    private String createCategory(String name) {
        CategoryDTO category = new CategoryDTO();
        category.setName(name);
        category.setCategoryType("DESPESA");

        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(category)
                .when().post("/categories")
                .then().statusCode(200)
                .extract().path("id");
    }

    private String registerAndLoginUser() {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String email = "user_" + uniqueId + "@test.com";

        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername("User " + uniqueId);
        user.setEmail(email);
        user.setPassword("123456");
        given().contentType(ContentType.JSON).body(user).post("/users/register").then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        return given().contentType(ContentType.JSON).body(login).post("/auth").then().extract().path("tokens.accessToken");
    }
}
