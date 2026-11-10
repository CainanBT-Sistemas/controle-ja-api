package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.AccountDTO;
import com.cainanbt.softwares.controleja.dtos.CategoryDTO;
import com.cainanbt.softwares.controleja.dtos.CreditCardDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.dtos.VehicleDTO;
import com.cainanbt.softwares.controleja.entities.Accounts;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountType;
import com.cainanbt.softwares.controleja.repositories.AccountsRepository;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementLimitsIntegrationTest extends BaseTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldEnforceFreeLimitsWithoutCountingDefaultsOrCardMirrorIncorrectly() {
        Session session = registerAndLogin("free-limits");

        createAccount(session.token(), "Conta 2").then().statusCode(200);
        String removableAccountId = createAccount(session.token(), "Conta 3")
                .then().statusCode(200).extract().path("id");
        createAccount(session.token(), "Conta bloqueada")
                .then().statusCode(400).body("title", is("Limite atingido"));

        given().header("Authorization", bearer(session.token()))
                .delete("/accounts/" + removableAccountId)
                .then().statusCode(200);
        createAccount(session.token(), "Conta substituta").then().statusCode(200);

        createCard(session.token(), "Cartao 1").then().statusCode(200);
        createCard(session.token(), "Cartao bloqueado")
                .then().statusCode(400).body("title", is("Limite atingido"));
        given().header("Authorization", bearer(session.token()))
                .get("/accounts")
                .then().statusCode(200).body("size()", is(3));

        List<String> parents = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            parents.add(createCategory(session.token(), "Categoria " + index, null)
                    .then().statusCode(200).extract().path("id"));
        }
        createCategory(session.token(), "Categoria bloqueada", null)
                .then().statusCode(400).body("title", is("Limite atingido"));

        createCategory(session.token(), "Subcategoria 1", parents.get(0)).then().statusCode(200);
        createCategory(session.token(), "Subcategoria 2", parents.get(0)).then().statusCode(200);
        createCategory(session.token(), "Subcategoria bloqueada", parents.get(0))
                .then().statusCode(400).body("title", is("Limite atingido"));

        createVehicle(session.token(), "Veiculo bloqueado")
                .then().statusCode(400).body("title", is("Limite atingido"));
    }

    @Test
    void shouldReconcileDowngradeAndUpgradeWithoutDeletingOrReactivatingData() {
        Session session = registerAndLogin("reconcile");
        setPlan(session.userId(), "PLUS");
        getEntitlements(session.token()).then().statusCode(200).body("plan", is("PLUS"));

        for (int index = 1; index <= 4; index++) {
            createAccount(session.token(), "Conta Plus " + index).then().statusCode(200);
        }
        for (int index = 1; index <= 3; index++) {
            createCard(session.token(), "Cartao Plus " + index).then().statusCode(200);
        }
        createVehicle(session.token(), "Veiculo Plus").then().statusCode(200);

        List<String> parents = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            parents.add(createCategory(session.token(), "Categoria Plus " + index, null)
                    .then().statusCode(200).extract().path("id"));
        }
        for (int index = 1; index <= 3; index++) {
            createCategory(session.token(), "Subcategoria Plus " + index, parents.get(0)).then().statusCode(200);
        }

        setPlan(session.userId(), "FREE");
        getEntitlements(session.token()).then().statusCode(200).body("plan", is("FREE"));

        assertRestrictionOrder("accounts", "user_id = ? AND deleted_at IS NULL AND type <> 'CREDIT_CARD'", session.userId(), 3);
        assertRestrictionOrder("credit_cards", "user_id = ? AND deleted_at IS NULL", session.userId(), 1);
        assertRestrictionOrder("vehicles", "user_id = ? AND deleted_at IS NULL", session.userId(), 0);
        assertRestrictionOrder("category", "user_id = ? AND deleted_at IS NULL AND is_default = false AND is_sub_category = false", session.userId(), 5);
        assertRestrictionOrder("category", "sub_category_id = ? AND deleted_at IS NULL AND is_default = false", UUID.fromString(parents.get(0)), 2);
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM credit_cards c JOIN accounts a ON a.id = c.account_id "
                        + "WHERE c.user_id = ? AND c.plan_restricted <> a.plan_restricted",
                Integer.class,
                session.userId()
        ));

        String restrictedAccountId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ? AND plan_restricted = true ORDER BY created_at, id LIMIT 1",
                String.class,
                session.userId()
        );
        given().header("Authorization", bearer(session.token()))
                .get("/accounts")
                .then().statusCode(200).body("planRestricted", hasItem(true));

        AccountDTO update = account("Nome bloqueado");
        given().header("Authorization", bearer(session.token()))
                .contentType(ContentType.JSON).body(update)
                .put("/accounts/" + restrictedAccountId)
                .then().statusCode(400).body("title", is("Recurso indisponível no plano"));
        given().header("Authorization", bearer(session.token()))
                .delete("/accounts/" + restrictedAccountId)
                .then().statusCode(200);

        String disabledAccountId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ? AND deleted_at IS NULL AND type <> 'CREDIT_CARD' "
                        + "ORDER BY created_at, id LIMIT 1",
                String.class,
                session.userId()
        );
        jdbc.update("UPDATE accounts SET enabled = false WHERE id = ?::uuid", disabledAccountId);
        setPlan(session.userId(), "PLUS");
        getEntitlements(session.token()).then().statusCode(200).body("plan", is("PLUS"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM accounts WHERE user_id = ? AND deleted_at IS NULL AND plan_restricted = true",
                Integer.class,
                session.userId()
        ));
        assertFalse(jdbc.queryForObject(
                "SELECT enabled FROM accounts WHERE id = ?::uuid",
                Boolean.class,
                disabledAccountId
        ));
    }

    @Test
    void shouldReconcileDowngradeBeforeUpdatingAResourceWithoutEntitlementFetch() {
        Session session = registerAndLogin("reconcile-on-mutation");
        setPlan(session.userId(), "PLUS");
        for (int index = 1; index <= 4; index++) {
            createAccount(session.token(), "Conta Plus " + index).then().statusCode(200);
        }

        String newestAccountId = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ? AND deleted_at IS NULL AND type <> 'CREDIT_CARD' "
                        + "ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class,
                session.userId()
        );
        setPlan(session.userId(), "FREE");

        given().header("Authorization", bearer(session.token()))
                .contentType(ContentType.JSON).body(account("Nome bloqueado"))
                .put("/accounts/" + newestAccountId)
                .then().statusCode(400).body("title", is("Recurso indisponível no plano"));
    }

    @Test
    void shouldSwitchTesterProfilesAndReturnToOrdinaryPlan() {
        Session session = registerAndLogin("tester-profile");
        UUID testerId = UUID.randomUUID();
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, session.userId());
        jdbc.update(
                "INSERT INTO closed_test_testers (id, email, normalized_email, enabled, profile, created_at) "
                        + "VALUES (?, ?, lower(?), true, 'TESTER_FREE', 1)",
                testerId,
                email,
                email
        );

        getEntitlements(session.token()).then()
                .statusCode(200)
                .body("plan", is("TESTER_FREE"))
                .body("limits.accounts", is(3));

        jdbc.update("UPDATE closed_test_testers SET profile = 'TESTER_PLUS' WHERE id = ?", testerId);
        getEntitlements(session.token()).then()
                .statusCode(200)
                .body("plan", is("TESTER_PLUS"))
                .body("limits.accounts", is(10));

        jdbc.update("UPDATE closed_test_testers SET enabled = false WHERE id = ?", testerId);
        getEntitlements(session.token()).then().statusCode(200).body("plan", is("FREE"));
    }

    @Test
    void shouldReturnConflictWhenTwoRequestsCompeteForLastAccountSlot() throws Exception {
        Session session = registerAndLogin("concurrency");
        createAccount(session.token(), "Conta 2").then().statusCode(200);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch releaseLoads = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                String name = "Conta concorrente " + index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return createAccountInCompetingTransaction(
                            session.userId(),
                            name,
                            bothLoaded,
                            releaseLoads
                    );
                }));
            }
            ready.await();
            start.countDown();
            bothLoaded.await();
            releaseLoads.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<Boolean> future : futures) {
                try {
                    if (future.get()) {
                        successes++;
                    }
                } catch (ExecutionException exception) {
                    if (hasOptimisticLockCause(exception)) {
                        conflicts++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(3, jdbc.queryForObject(
                    "SELECT count(*) FROM accounts WHERE user_id = ? AND deleted_at IS NULL AND type <> 'CREDIT_CARD'",
                    Integer.class,
                    session.userId()
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean createAccountInCompetingTransaction(
            UUID userId,
            String name,
            CountDownLatch bothLoaded,
            CountDownLatch releaseLoads
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Users user = usersRepository.findById(userId).orElseThrow();
            bothLoaded.countDown();
            try {
                releaseLoads.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            user.setEntitlementTouchedAt(user.getEntitlementTouchedAt() + 1);
            usersRepository.saveAndFlush(user);
            accountsRepository.saveAndFlush(Accounts.builder()
                    .id(UUID.randomUUID())
                    .name(name)
                    .type(AccountType.WALLET)
                    .institution("")
                    .currency("BRL")
                    .currentBalance(BigDecimal.ZERO)
                    .initialBalance(BigDecimal.ZERO)
                    .calculateBalance(true)
                    .enabled(true)
                    .user(user)
                    .createdAt(DateUtils.getEpochNow())
                    .build());
            return true;
        }));
    }

    private boolean hasOptimisticLockCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }

    private Session registerAndLogin(String prefix) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String email = prefix + "-" + unique + "@controleja.test";
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername(prefix + "-" + unique);
        user.setEmail(email);
        user.setPassword("123456");

        UUID userId = UUID.fromString(given().contentType(ContentType.JSON).body(user)
                .post("/users/register")
                .then().statusCode(200).extract().path("id"));
        verifyEmail(email);
        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        String token = given().contentType(ContentType.JSON).body(login)
                .post("/auth")
                .then().statusCode(200).extract().path("tokens.accessToken");
        return new Session(userId, token);
    }

    private Response createAccount(String token, String name) {
        return given().header("Authorization", bearer(token))
                .contentType(ContentType.JSON).body(account(name))
                .post("/accounts");
    }

    private AccountDTO account(String name) {
        AccountDTO dto = new AccountDTO();
        dto.setName(name);
        dto.setType(AccountType.WALLET);
        dto.setInitialBalance(BigDecimal.ZERO);
        dto.setInstitution("");
        return dto;
    }

    private Response createCard(String token, String name) {
        CreditCardDTO dto = new CreditCardDTO();
        dto.setName(name);
        dto.setTotalLimit(new BigDecimal("1000.00"));
        dto.setCloseDay(5);
        dto.setBestDay(12);
        return given().header("Authorization", bearer(token))
                .contentType(ContentType.JSON).body(dto)
                .post("/cards");
    }

    private Response createCategory(String token, String name, String parentId) {
        CategoryDTO dto = new CategoryDTO();
        dto.setName(name);
        dto.setCategoryType("DESPESA");
        if (parentId != null) {
            dto.setParentId(UUID.fromString(parentId));
        }
        return given().header("Authorization", bearer(token))
                .contentType(ContentType.JSON).body(dto)
                .post("/categories");
    }

    private Response createVehicle(String token, String name) {
        VehicleDTO dto = new VehicleDTO();
        dto.setName(name);
        dto.setBrand("Marca");
        dto.setModel("Modelo");
        dto.setYear(2026);
        dto.setCurrentOdometer(new BigDecimal("1000.0"));
        return given().header("Authorization", bearer(token))
                .contentType(ContentType.JSON).body(dto)
                .post("/vehicles");
    }

    private Response getEntitlements(String token) {
        return given().header("Authorization", bearer(token)).get("/users/me/entitlements");
    }

    private void setPlan(UUID userId, String plan) {
        jdbc.update("UPDATE users SET plan = ? WHERE id = ?", plan, userId);
    }

    private void assertRestrictionOrder(String table, String where, UUID ownerId, int availableCount) {
        List<Boolean> restrictions = jdbc.queryForList(
                "SELECT plan_restricted FROM " + table + " WHERE " + where + " ORDER BY created_at, id",
                Boolean.class,
                ownerId
        );
        for (int index = 0; index < restrictions.size(); index++) {
            assertEquals(index >= availableCount, restrictions.get(index));
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(UUID userId, String token) {
    }
}
