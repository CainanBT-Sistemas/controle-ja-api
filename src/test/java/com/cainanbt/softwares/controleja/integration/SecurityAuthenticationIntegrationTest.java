package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.dtos.InsertUpdateUserDTO;
import com.cainanbt.softwares.controleja.dtos.UserLoginDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class SecurityAuthenticationIntegrationTest extends BaseTest {

    @Value("${app.config.jwt.secret}")
    private String jwtSecret;

    @Value("${app.config.token.issue}")
    private String issuer;

    @Test
    @DisplayName("Rota protegida sem token deve retornar 401")
    void shouldReturn401WhenProtectedRouteHasNoToken() {
        given()
                .when().get("/accounts")
                .then().statusCode(401)
                .body("code", is(401))
                .body("title", is("Unauthorized"))
                .body("message", is("Token inválido ou expirado."));
    }

    @Test
    @DisplayName("Rota protegida com token inválido deve retornar 401")
    void shouldReturn401WhenTokenIsInvalid() {
        given().header("Authorization", "Bearer invalid.token.value")
                .when().get("/accounts")
                .then().statusCode(401)
                .body("code", is(401))
                .body("title", is("Unauthorized"))
                .body("message", is("Token inválido ou expirado."));
    }

    @Test
    @DisplayName("Rota protegida com token expirado deve retornar 401")
    void shouldReturn401WhenTokenIsExpired() {
        String expiredToken = buildExpiredToken("expired-security@test.com");

        given().header("Authorization", "Bearer " + expiredToken)
                .when().get("/accounts")
                .then().statusCode(401)
                .body("code", is(401))
                .body("title", is("Unauthorized"))
                .body("message", is("Token inválido ou expirado."));
    }

    @Test
    @DisplayName("Rota protegida com token válido deve permitir acesso")
    void shouldAllowProtectedRouteWhenTokenIsValid() {
        String token = registerAndLogin("valid-security@test.com", "valid_security_user");

        given().header("Authorization", "Bearer " + token)
                .when().get("/accounts")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("Rota pública deve continuar acessível sem token")
    void shouldAllowPublicRouteWithoutToken() {
        InsertUpdateUserDTO user = buildUser("public-security@test.com", "public_security_user");

        given().contentType(ContentType.JSON).body(user)
                .when().post("/users/register")
                .then().statusCode(200)
                .body("id", notNullValue());
    }

    @Test
    @DisplayName("Preflight CORS deve passar em rota protegida sem token")
    void shouldAllowCorsPreflightForProtectedRouteWithoutToken() {
        given()
                .header("Origin", "http://localhost:51038")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .when().options("/accounts/" + UUID.randomUUID())
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:51038")
                .header("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PUT"))
                .header("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("Authorization"));
    }

    private String registerAndLogin(String email, String username) {
        given().contentType(ContentType.JSON).body(buildUser(email, username))
                .post("/users/register")
                .then().statusCode(200);

        UserLoginDTO login = UserLoginDTO.builder()
                .email(email)
                .password("123456")
                .build();

        return given().contentType(ContentType.JSON).body(login)
                .when().post("/auth")
                .then().statusCode(200)
                .extract().path("tokens.accessToken");
    }

    private InsertUpdateUserDTO buildUser(String email, String username) {
        InsertUpdateUserDTO user = new InsertUpdateUserDTO();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("123456");
        return user;
    }

    private String buildExpiredToken(String email) {
        Date now = new Date();
        Date expiredAt = new Date(now.getTime() - 60_000);

        return Jwts.builder()
                .setSubject(email)
                .setIssuer(issuer)
                .setIssuedAt(new Date(now.getTime() - 120_000))
                .setExpiration(expiredAt)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .claim("id", UUID.randomUUID())
                .claim("username", email)
                .compact();
    }
}
