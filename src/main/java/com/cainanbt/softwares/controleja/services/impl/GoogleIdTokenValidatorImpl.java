package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.services.GoogleIdTokenValidator;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class GoogleIdTokenValidatorImpl implements GoogleIdTokenValidator {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder jwtDecoder;
    private final String expectedAudience;

    @Autowired
    public GoogleIdTokenValidatorImpl(@Value("${app.config.google.id-token.audience:}") String expectedAudience) {
        this(NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build(), expectedAudience);
    }

    GoogleIdTokenValidatorImpl(JwtDecoder jwtDecoder, String expectedAudience) {
        this.jwtDecoder = jwtDecoder;
        this.expectedAudience = expectedAudience == null ? "" : expectedAudience.trim();
    }

    @Override
    public GoogleIdentityDTO validate(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            logValidationFailure("TOKEN_MISSING");
            throw invalidGoogleToken();
        }
        if (expectedAudience.isBlank()) {
            logValidationFailure("EXPECTED_AUDIENCE_MISSING");
            throw new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.GOOGLE_LOGIN_NOT_CONFIGURED);
        }

        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            validateIssuer(jwt);
            validateAudience(jwt);
            validateExpiration(jwt);

            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                logValidationFailure("REQUIRED_CLAIMS_MISSING");
                throw invalidGoogleToken();
            }

            return new GoogleIdentityDTO(
                    subject,
                    email.trim().toLowerCase(),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (BadRequestException e) {
            throw e;
        } catch (JwtValidationException e) {
            logValidationFailure(isExpired(e) ? "TOKEN_EXPIRED" : "TOKEN_DECODE_REJECTED");
            throw invalidGoogleToken();
        } catch (JwtException | IllegalArgumentException e) {
            logValidationFailure(hasCause(e, ResourceAccessException.class) || hasCause(e, IOException.class)
                    ? "JWKS_ACCESS_FAILED"
                    : "TOKEN_DECODE_REJECTED");
            throw invalidGoogleToken();
        }
    }

    private void validateIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
            logValidationFailure("ISSUER_INVALID");
            throw invalidGoogleToken();
        }
    }

    private void validateAudience(Jwt jwt) {
        if (jwt.getAudience() == null || jwt.getAudience().isEmpty()) {
            logValidationFailure("TOKEN_AUDIENCE_MISSING");
            throw invalidGoogleToken();
        }
        if (!jwt.getAudience().contains(expectedAudience)) {
            logValidationFailure("TOKEN_AUDIENCE_MISMATCH");
            throw invalidGoogleToken();
        }
    }

    private void validateExpiration(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            logValidationFailure("TOKEN_EXPIRATION_MISSING");
            throw invalidGoogleToken();
        }
        if (!expiresAt.isAfter(Instant.now())) {
            logValidationFailure("TOKEN_EXPIRED");
            throw invalidGoogleToken();
        }
    }

    private boolean isExpired(JwtValidationException exception) {
        return exception.getErrors().stream()
                .map(OAuth2Error::getDescription)
                .filter(description -> description != null)
                .map(String::toLowerCase)
                .anyMatch(description -> description.contains("expired"));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logValidationFailure(String reason) {
        log.warn("Google idToken rejected reason={}", reason);
    }

    private BadRequestException invalidGoogleToken() {
        return new BadRequestException(ConstsMessages.ACCESS_DENIED_TITLE, ConstsMessages.INVALID_GOOGLE_TOKEN);
    }
}
