package com.cainanbt.softwares.controleja.services.billing;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PubSubAuthorization {
    private static final String ISSUER = "https://accounts.google.com";
    private final BillingSettings settings;
    private final JwtDecoder decoder;

    public PubSubAuthorization(BillingSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        NimbusJwtDecoder google = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(new RestTemplate(factory)).build();
        google.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = google;
    }

    public void requireAuthorized(String authorization) {
        if (!settings.enabled()) throw BillingProblem.disabled();
        if (authorization == null || !authorization.startsWith("Bearer ")) throw denied();
        try {
            validateClaims(decoder.decode(authorization.substring(7)), settings);
        } catch (JwtException | IllegalArgumentException e) {
            throw denied();
        }
    }

    static void validateClaims(Jwt jwt, BillingSettings settings) {
        if (settings.pubsubAudience() == null || settings.pubsubAudience().isBlank()
                || settings.pubsubServiceAccount() == null || settings.pubsubServiceAccount().isBlank()
                || jwt.getExpiresAt() == null
                || jwt.getAudience() == null || !jwt.getAudience().contains(settings.pubsubAudience())
                || !settings.pubsubServiceAccount().equals(jwt.getClaimAsString("email"))
                || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw denied();
        }
    }

    private static BillingProblem denied() {
        return new BillingProblem(HttpStatus.UNAUTHORIZED, "BILLING_NOTIFICATION_UNAUTHORIZED",
                "Notificação sem autenticação válida.");
    }
}
