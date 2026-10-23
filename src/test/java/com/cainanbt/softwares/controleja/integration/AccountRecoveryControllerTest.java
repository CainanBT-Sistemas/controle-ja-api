package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.services.AccountActionEmailSender;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(AccountRecoveryControllerTest.MailTestConfig.class)
class AccountRecoveryControllerTest extends BaseTest {
    @Autowired
    private FakeAccountActionEmailSender emailSender;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearMessages() {
        emailSender.clear();
    }

    @Test
    void resetsLocalPasswordAndConsumesCodeOnlyOnce() {
        String email = register("reset");
        var previousSession = login(email, "123456").statusCode(200).extract();
        String oldAccessToken = previousSession.path("tokens.accessToken");
        String oldRefreshToken = previousSession.path("tokens.refreshToken");
        request(email, "PASSWORD_RESET").statusCode(202);
        String code = emailSender.codeFor(email);
        assertNotNull(code);

        given().contentType(ContentType.JSON).body(Map.of(
                        "email", email,
                        "code", code,
                        "newPassword", "654321"
                )).post("/auth/password-reset/confirm")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + oldAccessToken)
                .get("/accounts")
                .then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("token", oldRefreshToken))
                .post("/auth/auto-login")
                .then().statusCode(400);

        login(email, "123456").statusCode(400);
        login(email, "654321").statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of(
                        "email", email,
                        "code", code,
                        "newPassword", "777777"
                )).post("/auth/password-reset/confirm")
                .then().statusCode(400)
                .body("message", is("Código inválido ou expirado."));
    }

    @Test
    void requestDoesNotRevealWhetherAccountExists() {
        String email = register("known");
        String knownMessage = request(email, "PASSWORD_RESET")
                .statusCode(202).extract().path("message");
        String unknownMessage = request("unknown-" + UUID.randomUUID() + "@test.com", "PASSWORD_RESET")
                .statusCode(202).extract().path("message");

        assertEquals(knownMessage, unknownMessage);
    }

    @Test
    void resendInvalidatesPreviousCode() {
        String email = register("resend");
        request(email, "PASSWORD_RESET").statusCode(202);
        String firstCode = emailSender.codeFor(email);
        request(email, "PASSWORD_RESET").statusCode(202);
        String secondCode = emailSender.codeFor(email);

        given().contentType(ContentType.JSON).body(Map.of(
                        "email", email,
                        "code", firstCode,
                        "newPassword", "654321"
                )).post("/auth/password-reset/confirm")
                .then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of(
                        "email", email,
                        "code", secondCode,
                        "newPassword", "654321"
                )).post("/auth/password-reset/confirm")
                .then().statusCode(200);
    }

    @Test
    void blocksCodeAfterFiveInvalidAttempts() {
        String email = register("attempts");
        request(email, "PASSWORD_RESET").statusCode(202);
        String validCode = emailSender.codeFor(email);

        for (int attempt = 0; attempt < 5; attempt++) {
            given().contentType(ContentType.JSON).body(Map.of(
                            "email", email,
                            "code", "999999".equals(validCode) ? "888888" : "999999",
                            "newPassword", "654321"
                    )).post("/auth/password-reset/confirm")
                    .then().statusCode(400);
        }

        given().contentType(ContentType.JSON).body(Map.of(
                        "email", email,
                        "code", validCode,
                        "newPassword", "654321"
                )).post("/auth/password-reset/confirm")
                .then().statusCode(400);
    }

    @Test
    void guidesGoogleOnlyAccountWithoutCreatingLocalPassword() {
        String email = register("google-only");
        jdbcTemplate.update(
                "UPDATE users SET oauth2user = true, oauth2provider = 'GOOGLE', local_password_enabled = false WHERE email = ?",
                email
        );

        request(email, "PASSWORD_RESET").statusCode(202);

        assertTrue(emailSender.guidanceSent(email));
        assertEquals(null, emailSender.codeFor(email));
    }

    @Test
    void reactivatesSameUserAndPreservesOperationalData() {
        String email = register("reactivate");
        String token = login(email, "123456").statusCode(200).extract().path("tokens.accessToken");
        String userId = login(email, "123456").statusCode(200).extract().path("id");
        Integer accountCountBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM accounts WHERE user_id = ?::uuid",
                Integer.class,
                userId
        );
        given().header("Authorization", "Bearer " + token).delete("/users/" + userId)
                .then().statusCode(200);

        login(email, "123456")
                .statusCode(400)
                .body("title", is("Conta excluída"))
                .body(
                        "message",
                        is("Esta conta foi excluída. Para acessar novamente, use Recuperar acesso e reative sua conta.")
                );

        request(email, "ACCOUNT_REACTIVATION").statusCode(202);
        String code = emailSender.codeFor(email);
        given().contentType(ContentType.JSON).body(Map.of("email", email, "code", code))
                .post("/auth/reactivation/confirm")
                .then().statusCode(200);

        login(email, "123456").statusCode(200);
        Integer accountCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM accounts WHERE user_id = ?::uuid",
                Integer.class,
                userId
        );
        assertEquals(accountCountBefore, accountCountAfter);
    }

    @Test
    void doesNotReactivateAccountOutsideRetentionWindow() {
        String email = register("expired-reactivation");
        String userId = login(email, "123456").statusCode(200).extract().path("id");
        jdbcTemplate.update(
                "UPDATE users SET deleted_at = ?, enabled = false, account_non_locked = false WHERE id = ?::uuid",
                System.currentTimeMillis() - 61L * 86_400_000L,
                userId
        );

        request(email, "ACCOUNT_REACTIVATION").statusCode(202);

        assertEquals(null, emailSender.codeFor(email));
    }

    @Test
    void concurrentConfirmationHasSingleWinner() throws Exception {
        String email = register("concurrent");
        request(email, "PASSWORD_RESET").statusCode(202);
        String code = emailSender.codeFor(email);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<Integer>) () -> given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("email", email, "code", code, "newPassword", "654321"))
                    .post("/auth/password-reset/confirm")
                    .statusCode();
            var firstResult = executor.submit(task);
            var secondResult = executor.submit(task);
            int first = firstResult.get();
            int second = secondResult.get();
            assertTrue((first == 200 && second == 400) || (first == 400 && second == 200));
        } finally {
            executor.shutdownNow();
        }
    }

    private String register(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@test.com";
        InsertUpdateUserDTO dto = new InsertUpdateUserDTO();
        dto.setUsername(prefix);
        dto.setEmail(email);
        dto.setPassword("123456");
        given().contentType(ContentType.JSON).body(dto).post("/users/register")
                .then().statusCode(200);
        return email;
    }

    private io.restassured.response.ValidatableResponse request(String email, String purpose) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "purpose", purpose))
                .post("/auth/recovery/request")
                .then();
    }

    private io.restassured.response.ValidatableResponse login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(UserLoginDTO.builder().email(email).password(password).build())
                .post("/auth")
                .then();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailTestConfig {
        @Bean
        @Primary
        FakeAccountActionEmailSender fakeAccountActionEmailSender() {
            return new FakeAccountActionEmailSender();
        }
    }

    static class FakeAccountActionEmailSender implements AccountActionEmailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, Boolean> guidance = new ConcurrentHashMap<>();

        @Override
        public void sendCode(String recipient, String code, AccountActionPurpose purpose, int expiryMinutes) {
            codes.put(recipient, code);
        }

        @Override
        public void sendGoogleAccessGuidance(String recipient) {
            guidance.put(recipient, true);
        }

        String codeFor(String email) {
            return codes.get(email);
        }

        boolean guidanceSent(String email) {
            return guidance.getOrDefault(email, false);
        }

        void clear() {
            codes.clear();
            guidance.clear();
        }
    }
}
