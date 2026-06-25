package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.config.TestUserFixtures;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.impl.JwtServiceImp;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestPropertySource(properties = "app.config.closed-test.enabled=true")
class ClosedTestAccessIntegrationTest extends BaseTest {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtServiceImp jwtService;

    @Test
    void shouldAllowAuthorizedRegistrationLoginAndAutoLoginWithTesterEntitlement() {
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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
