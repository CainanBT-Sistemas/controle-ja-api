package com.cainanbt.softwares.controleja.services.billing;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillingSecurityTest {
    private static final BillingSettings SETTINGS = new BillingSettings(true, "br.com.controleja.app",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "https://billing.test/push",
            "push@billing.iam.gserviceaccount.com");

    @Test
    void encryptsWithDistinctNoncesAndBindsCiphertextToFingerprint() {
        BillingTokens secrets = new BillingTokens(SETTINGS);
        String token = "test-token-that-must-not-go-in-the-database";
        String fingerprint = BillingTokens.fingerprint(token);
        byte[] first = secrets.encrypt(token, fingerprint);
        byte[] second = secrets.encrypt(token, fingerprint);
        assertNotEquals(java.util.HexFormat.of().formatHex(first), java.util.HexFormat.of().formatHex(second));
        assertFalse(new String(first, StandardCharsets.UTF_8).contains(token));
        assertEquals(token, secrets.decrypt(first, fingerprint));
        assertThrows(BillingProblem.class, () -> secrets.decrypt(first, "different-context"));

        UUID user = UUID.randomUUID();
        assertEquals(secrets.accountId(user), new BillingTokens(SETTINGS).accountId(user));
        assertNotEquals(secrets.accountId(user), secrets.accountId(UUID.randomUUID()));
        assertFalse(SETTINGS.toString().contains(SETTINGS.tokenKey()));
    }

    @Test
    void checksPubSubAudienceAndVerifiedServiceAccountAfterSignatureValidation() {
        assertDoesNotThrow(() -> PubSubAuthorization.validateClaims(
                signedClaims(SETTINGS.pubsubAudience(), SETTINGS.pubsubServiceAccount(), true), SETTINGS));
        assertThrows(BillingProblem.class, () -> PubSubAuthorization.validateClaims(
                signedClaims("different-audience", SETTINGS.pubsubServiceAccount(), true), SETTINGS));
        assertThrows(BillingProblem.class, () -> PubSubAuthorization.validateClaims(
                signedClaims(SETTINGS.pubsubAudience(), "other@billing.test", true), SETTINGS));
        assertThrows(BillingProblem.class, () -> PubSubAuthorization.validateClaims(
                signedClaims(SETTINGS.pubsubAudience(), SETTINGS.pubsubServiceAccount(), false), SETTINGS));
    }

    private static Jwt signedClaims(String audience, String email, boolean verified) {
        return Jwt.withTokenValue("verified-by-decoder").header("alg", "RS256")
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("aud", List.of(audience)).claim("email", email).claim("email_verified", verified).build();
    }
}
