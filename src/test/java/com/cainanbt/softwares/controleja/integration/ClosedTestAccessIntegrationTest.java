package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.config.TestUserFixtures;
import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
import com.cainanbt.softwares.controleja.dtos.GoogleLoginDTO;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.entities.ClosedTestTester;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.repositories.ClosedTestTesterRepository;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.GoogleIdTokenValidator;
import com.cainanbt.softwares.controleja.services.impl.JwtServiceImp;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "app.config.closed-test.enabled=true")
class ClosedTestAccessIntegrationTest extends BaseTest {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtServiceImp jwtService;

    @Autowired
    private ClosedTestTesterRepository testerRepository;

    @MockBean
    private GoogleIdTokenValidator googleIdTokenValidator;

    @BeforeEach
    void clearTesters() {
        testerRepository.deleteAll();
        reset(googleIdTokenValidator);
    }

    @Test
    void shouldAllowAuthorizedRegistrationLoginAndAutoLoginWithTesterEntitlement() {
        allow(TestUserFixtures.TESTER_EMAIL);
        InsertUpdateUserDTO user = buildRegistration(
                TestUserFixtures.TESTER_EMAIL.toUpperCase(),
                "Closed Test User"
        );

        given().contentType(ContentType.JSON).body(user)
                .when().post("/users/register")
                .then().statusCode(200)
                .body("email", is(TestUserFixtures.TESTER_EMAIL));

        String refreshToken = given().contentType(ContentType.JSON)
                .body(UserLoginDTO.builder()
                        .email("  " + TestUserFixtures.TESTER_EMAIL.toUpperCase() + " ")
                        .password("123456")
                        .build())
                .when().post("/auth")
                .then().statusCode(200)
                .body("entitlements.plan", is("TESTER"))
                .body("entitlements.tester", is(true))
                .body("tokens.accessToken", notNullValue())
                .extract().path("tokens.refreshToken");

        given().contentType(ContentType.JSON).body(Map.of("token", refreshToken))
                .when().post("/auth/auto-login")
                .then().statusCode(200)
                .body("entitlements.plan", is("TESTER"))
                .body("tokens.refreshToken", notNullValue());
    }

    @Test
    void shouldDenyRegistrationWithoutPersistingUserOrDefaultData() {
        String deniedEmail = uniqueEmail("denied-registration");

        given().contentType(ContentType.JSON)
                .body(buildRegistration(deniedEmail, "Denied Registration"))
                .when().post("/users/register")
                .then().statusCode(403)
                .body("code", is(403))
                .body("title", is(ConstsMessages.CLOSED_TEST_TITLE))
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));

        assertEquals(0, usersRepository.findByEmailIgnoreCase(deniedEmail).stream().count());
    }

    @Test
    void shouldDenyEveryoneWhenClosedTestTableIsEmpty() {
        given().contentType(ContentType.JSON)
                .body(buildRegistration(uniqueEmail("empty-table"), "Empty Table"))
                .when().post("/users/register")
                .then().statusCode(403)
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));
    }

    @Test
    void shouldEnforceNormalizedEmailUniqueness() {
        testerRepository.saveAndFlush(TestUserFixtures.activeTester(" Unique@Test.com "));

        ClosedTestTester duplicate = TestUserFixtures.activeTester("unique@test.com");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> testerRepository.saveAndFlush(duplicate)
        );
    }

    @Test
    void shouldAllowAndDenyGoogleUsingValidatedClaimEmail() {
        String allowedEmail = uniqueEmail("google-allowed");
        allow(allowedEmail);
        when(googleIdTokenValidator.validate("allowed-google-token"))
                .thenReturn(new GoogleIdentityDTO(
                        "allowed-google-subject",
                        allowedEmail,
                        "Allowed Google",
                        null
                ));

        given().contentType(ContentType.JSON)
                .body(googleRequest("client-supplied@example.com", "allowed-google-token"))
                .when().post("/auth/google")
                .then().statusCode(200)
                .body("email", is(allowedEmail))
                .body("entitlements.plan", is("TESTER"));

        String deniedEmail = uniqueEmail("google-denied");
        when(googleIdTokenValidator.validate("denied-google-token"))
                .thenReturn(new GoogleIdentityDTO(
                        "denied-google-subject",
                        deniedEmail,
                        "Denied Google",
                        null
                ));

        given().contentType(ContentType.JSON)
                .body(googleRequest(allowedEmail, "denied-google-token"))
                .when().post("/auth/google")
                .then().statusCode(403)
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));

        assertTrue(usersRepository.findByEmailIgnoreCase(deniedEmail).isEmpty());
    }

    @Test
    void shouldDenyPasswordLoginForExistingUserOutsideAllowlistWithoutIssuingTokens() {
        Users deniedUser = createExistingUser(uniqueEmail("denied-login"));

        given().contentType(ContentType.JSON)
                .body(UserLoginDTO.builder()
                        .email(deniedUser.getEmail())
                        .password("123456")
                        .build())
                .when().post("/auth")
                .then().statusCode(403)
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));

        Users reloaded = usersRepository.findById(deniedUser.getId()).orElseThrow();
        assertNull(reloaded.getRefreshToken());
    }

    @Test
    void shouldInvalidateRefreshTokenWhenRemovedUserAttemptsAutoLogin() {
        Users deniedUser = createExistingUser(uniqueEmail("removed-auto-login"));
        String refreshToken = jwtService.generateRefreshToken(new UserAuthenticateDTO(deniedUser));
        deniedUser.setRefreshToken(refreshToken);
        deniedUser.setRefreshTokenExpiry(jwtService.getRefreshExpiration());
        usersRepository.save(deniedUser);

        given().contentType(ContentType.JSON).body(Map.of("token", refreshToken))
                .when().post("/auth/auto-login")
                .then().statusCode(403)
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));

        Users reloaded = usersRepository.findById(deniedUser.getId()).orElseThrow();
        assertNull(reloaded.getRefreshToken());
        assertEquals(0L, reloaded.getRefreshTokenExpiry());
    }

    @Test
    void shouldDenyPrivateRouteWithPreviouslyIssuedAccessToken() {
        Users deniedUser = createExistingUser(uniqueEmail("removed-private-route"));
        String accessToken = jwtService.generateAccessToken(new UserAuthenticateDTO(deniedUser));

        given().header("Authorization", "Bearer " + accessToken)
                .when().get("/accounts")
                .then().statusCode(403)
                .body("code", is(403))
                .body("title", is(ConstsMessages.CLOSED_TEST_TITLE))
                .body("message", is(ConstsMessages.CLOSED_TEST_ACCESS_DENIED));
    }

    @Test
    void shouldKeepHealthCheckPublicWhileClosedTestIsEnabled() {
        given().basePath("")
                .when().get("/actuator/health")
                .then().statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void shouldApplyDeactivationAndReactivationWithoutRestart() {
        String email = uniqueEmail("dynamic-tester");
        ClosedTestTester tester = allow(email);

        String userId = given().contentType(ContentType.JSON)
                .body(buildRegistration(email, "Dynamic Tester"))
                .when().post("/users/register")
                .then().statusCode(200)
                .extract().path("id");

        UserLoginDTO login = UserLoginDTO.builder().email(email).password("123456").build();
        var loginResponse = given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(200)
                .extract().response();
        String accessToken = loginResponse.path("tokens.accessToken");
        String refreshToken = loginResponse.path("tokens.refreshToken");

        tester.setEnabled(false);
        tester.setUpdatedAt(DateUtils.getEpochNow());
        tester.setDisabledAt(DateUtils.getEpochNow());
        testerRepository.saveAndFlush(tester);

        given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("token", refreshToken))
                .when().post("/auth/auto-login")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + accessToken)
                .when().get("/accounts")
                .then().statusCode(403);

        tester.setEnabled(true);
        tester.setUpdatedAt(DateUtils.getEpochNow());
        tester.setDisabledAt(null);
        testerRepository.saveAndFlush(tester);

        given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(200)
                .body("id", is(userId))
                .body("entitlements.plan", is("TESTER"));
    }

    private Users createExistingUser(String email) {
        return usersRepository.save(Users.builder()
                .id(ID.generate())
                .username("Existing User")
                .email(email)
                .password(passwordEncoder.encode("123456"))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .role("USER")
                .oauth2User(false)
                .createdAt(DateUtils.getEpochNow())
                .build());
    }

    private InsertUpdateUserDTO buildRegistration(String email, String username) {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("123456");
        return user;
    }

    private ClosedTestTester allow(String email) {
        return testerRepository.saveAndFlush(TestUserFixtures.activeTester(email));
    }

    private GoogleLoginDTO googleRequest(String email, String idToken) {
        GoogleLoginDTO dto = new GoogleLoginDTO();
        dto.setEmail(email);
        dto.setGoogleId("client-google-id");
        dto.setIdToken(idToken);
        dto.setDisplayName("Client Name");
        return dto;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
