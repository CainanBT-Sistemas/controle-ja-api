package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenValidatorImplTest {

    private static final String EXPECTED_AUDIENCE = "129979310679-1jvva61ptka1qulph59takl8d4g1urb4.apps.googleusercontent.com";

    @Test
    void shouldValidateGoogleIdTokenAndExtractIdentity() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://accounts.google.com", Instant.now().plusSeconds(300)),
                EXPECTED_AUDIENCE
        );

        GoogleIdentityDTO identity = validator.validate("valid-token");

        assertEquals("google-subject", identity.subject());
        assertEquals("google@test.com", identity.email());
        assertEquals("Google User", identity.displayName());
        assertEquals("https://example.com/photo.png", identity.photoUrl());
    }

    @Test
    void shouldRejectInvalidToken() {
        JwtDecoder decoder = token -> {
            throw new JwtException("invalid signature");
        };
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(decoder, EXPECTED_AUDIENCE);

        assertThrows(BadRequestException.class, () -> validator.validate("invalid-token"));
    }

    @Test
    void shouldRejectWrongAudience() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt("another-client-id.apps.googleusercontent.com", "https://accounts.google.com", Instant.now().plusSeconds(300)),
                EXPECTED_AUDIENCE
        );

        assertThrows(BadRequestException.class, () -> validator.validate("wrong-audience-token"));
    }

    @Test
    void shouldRejectExpiredToken() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://accounts.google.com", Instant.now().minusSeconds(1)),
                EXPECTED_AUDIENCE
        );

        assertThrows(BadRequestException.class, () -> validator.validate("expired-token"));
    }

    @Test
    void shouldRejectWrongIssuer() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://malicious.example.com", Instant.now().plusSeconds(300)),
                EXPECTED_AUDIENCE
        );

        assertThrows(BadRequestException.class, () -> validator.validate("wrong-issuer-token"));
    }

    private Jwt googleJwt(String audience, String issuer, Instant expiresAt) {
        Instant issuedAt = Instant.now().minusSeconds(30);
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("google-subject")
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", "Google@Test.com")
                .claim("name", "Google User")
                .claim("picture", "https://example.com/photo.png")
                .build();
    }
}
