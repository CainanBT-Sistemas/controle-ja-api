package com.cainanbt.softwares.controleja.services.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertRejectsWithReason(validator, "invalid-token", "TOKEN_DECODE_REJECTED");
    }

    @Test
    void shouldRejectWrongAudience() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt("another-client-id.apps.googleusercontent.com", "https://accounts.google.com", Instant.now().plusSeconds(300)),
                EXPECTED_AUDIENCE
        );

        assertRejectsWithReason(validator, "wrong-audience-token", "TOKEN_AUDIENCE_MISMATCH");
    }

    @Test
    void shouldRejectMissingExpectedAudienceAsEnvironmentConfigurationFailure() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://accounts.google.com", Instant.now().plusSeconds(300)),
                ""
        );

        assertRejectsWithReason(validator, "valid-token", "EXPECTED_AUDIENCE_MISSING");
    }

    @Test
    void shouldRejectTokenWithoutAudience() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(Collections.emptyList(), "https://accounts.google.com", Instant.now().plusSeconds(300), true),
                EXPECTED_AUDIENCE
        );

        assertRejectsWithReason(validator, "missing-audience-token", "TOKEN_AUDIENCE_MISSING");
    }

    @Test
    void shouldRejectExpiredToken() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://accounts.google.com", Instant.now().minusSeconds(1)),
                EXPECTED_AUDIENCE
        );

        assertRejectsWithReason(validator, "expired-token", "TOKEN_EXPIRED");
    }

    @Test
    void shouldRejectWrongIssuer() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(EXPECTED_AUDIENCE, "https://malicious.example.com", Instant.now().plusSeconds(300)),
                EXPECTED_AUDIENCE
        );

        assertRejectsWithReason(validator, "wrong-issuer-token", "ISSUER_INVALID");
    }

    @Test
    void shouldRejectMissingRequiredClaims() {
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(
                token -> googleJwt(List.of(EXPECTED_AUDIENCE), "https://accounts.google.com", Instant.now().plusSeconds(300), false),
                EXPECTED_AUDIENCE
        );

        assertRejectsWithReason(validator, "missing-claims-token", "REQUIRED_CLAIMS_MISSING");
    }

    @Test
    void shouldRejectWhenGoogleJwksCannotBeAccessed() {
        JwtDecoder decoder = token -> {
            throw new JwtException(
                    "Unable to retrieve remote JWK set",
                    new ResourceAccessException("JWKS unavailable", new IOException("simulated"))
            );
        };
        GoogleIdTokenValidatorImpl validator = new GoogleIdTokenValidatorImpl(decoder, EXPECTED_AUDIENCE);

        assertRejectsWithReason(validator, "valid-token", "JWKS_ACCESS_FAILED");
    }

    private void assertRejectsWithReason(
            GoogleIdTokenValidatorImpl validator,
            String token,
            String expectedReason
    ) {
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleIdTokenValidatorImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThrows(BadRequestException.class, () -> validator.validate(token));
            assertTrue(
                    appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .anyMatch(message -> message.equals("Google idToken rejected reason=" + expectedReason)),
                    "Expected diagnostic reason " + expectedReason
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private Jwt googleJwt(String audience, String issuer, Instant expiresAt) {
        return googleJwt(List.of(audience), issuer, expiresAt, true);
    }

    private Jwt googleJwt(List<String> audience, String issuer, Instant expiresAt, boolean includeRequiredClaims) {
        Instant issuedAt = Instant.now().minusSeconds(30);
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
        if (includeRequiredClaims) {
            builder.subject("google-subject")
                    .claim("email", "Google@Test.com");
        }
        return builder
                .claim("name", "Google User")
                .claim("picture", "https://example.com/photo.png")
                .build();
    }
}
