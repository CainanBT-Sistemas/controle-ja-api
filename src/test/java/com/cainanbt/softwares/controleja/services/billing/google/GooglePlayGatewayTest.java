package com.cainanbt.softwares.controleja.services.billing.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlayGatewayTest {
    private static final String BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/br.com.controleja.app/purchases/";
    private static final String OAUTH = "https://oauth2.googleapis.com/token";
    private static final String PACKAGE = "br.com.controleja.app";
    private static final String ONE_TIME = "controleja_no_ads_lifetime";
    private static final String PLUS = "controleja_plus";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer google;
    private GooglePlayGateway gateway;

    @BeforeEach
    void initialize() throws Exception {
        var template = new RestTemplate();
        google = MockRestServiceServer.bindTo(template).build();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = pair.getPrivate().getEncoded();
        var mapper = new ObjectMapper();
        var credentials = mapper.writeValueAsString(Map.of(
                "client_email", "billing@example.iam.gserviceaccount.com",
                "private_key", "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key)
                        + "\n-----END PRIVATE KEY-----"
        ));
        gateway = new GooglePlayGateway(mapper, credentials, template.getRequestFactory(), CLOCK);
        google.expect(once(), requestTo(OAUTH)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    try {
                        String form = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                        assertThat(form).startsWith("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=");
                        var jwt = SignedJWT.parse(URLDecoder.decode(form.split("&assertion=", 2)[1], StandardCharsets.UTF_8));
                        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
                        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
                        var claims = jwt.getJWTClaimsSet();
                        assertThat(claims.getAudience()).containsExactly(OAUTH);
                        assertThat(claims.getIssuer()).isEqualTo("billing@example.iam.gserviceaccount.com");
                        assertThat(claims.getStringClaim("scope")).isEqualTo("https://www.googleapis.com/auth/androidpublisher");
                        assertThat(claims.getIssueTime().toInstant()).isEqualTo(CLOCK.instant());
                        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(CLOCK.instant().plusSeconds(3600));
                    } catch (Exception exception) {
                        throw new AssertionError("JWT OAuth inválido na requisição de teste.");
                    }
                })
                .andRespond(withSuccess("""
                        {"access_token":"mock-access","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @CsvSource({"PURCHASED,1,true", "PENDING,1,false", "CANCELLED,0,false", "PURCHASED,0,false"})
    void usesOfficialProductStateAndRefundQuantity(String state, int refundableQuantity, boolean active) {
        upstream("productsv2/tokens/demo", """
                {
                    "purchaseStateContext":{"purchaseState":"%s"},
                    "productLineItem":[{"productId":"%s","productOfferDetails":{"refundableQuantity":%d}}],
                    "obfuscatedExternalAccountId":"account",
                    "purchaseCompletionTime":"2025-12-31T00:00:00Z",
                    "acknowledgementState":"ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
                }
                """.formatted(state, ONE_TIME, refundableQuantity));
        var result = gateway.verify(PACKAGE, ONE_TIME, "demo");
        assertThat(result.active()).isEqualTo(active);
        assertThat(result.acknowledged()).isTrue();
        assertThat(result.state()).isEqualTo(state);
        assertThat(result.obfuscatedAccountId()).isEqualTo("account");
        google.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "SUBSCRIPTION_STATE_ACTIVE,monthly,2026-02-01T00:00:00Z,true",
            "SUBSCRIPTION_STATE_ACTIVE,yearly,2027-01-01T00:00:00Z,true",
            "SUBSCRIPTION_STATE_CANCELED,monthly,2026-02-01T00:00:00Z,true",
            "SUBSCRIPTION_STATE_CANCELED,monthly,2026-01-01T00:00:00Z,false",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD,monthly,2026-02-01T00:00:00Z,true",
            "SUBSCRIPTION_STATE_ON_HOLD,monthly,2026-02-01T00:00:00Z,false",
            "SUBSCRIPTION_STATE_PAUSED,monthly,2026-02-01T00:00:00Z,false",
            "SUBSCRIPTION_STATE_EXPIRED,monthly,2025-12-31T00:00:00Z,false",
            "SUBSCRIPTION_STATE_PENDING,monthly,2026-02-01T00:00:00Z,false"
    })
    void subscriptionUsesStateAndExpiry(String state, String basePlan, String expiry, boolean active) {
        upstream("subscriptionsv2/tokens/demo", subscription(state, basePlan, expiry));
        var result = gateway.verify(PACKAGE, PLUS, "demo");
        assertThat(result.active()).isEqualTo(active);
        assertThat(result.basePlanId()).isEqualTo(basePlan);
        assertThat(result.expiresAt()).isEqualTo(Instant.parse(expiry));
        assertThat(result.linkedPurchaseToken()).isEqualTo("previous");
        assertThat(result.autoRenew()).isTrue();
        assertThat(result.subscription()).isTrue();
        google.verify();
    }

    @Test
    void acknowledgeReusesOAuthAndUsesOfficialEndpoints() {
        google.expect(requestTo(BASE + "products/" + ONE_TIME + "/tokens/demo:acknowledge"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer mock-access"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        google.expect(requestTo(BASE + "subscriptions/" + PLUS + "/tokens/demo:acknowledge"))
                .andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        gateway.acknowledge(PACKAGE, ONE_TIME, "demo", false);
        gateway.acknowledge(PACKAGE, PLUS, "demo", true);
        google.verify();
    }

    @Test
    void ignoresRefundForEarlierSubscriptionOrderWithSameToken() {
        upstream("voidedpurchases?type=1", """
                {"voidedPurchases":[{"purchaseToken":"demo","orderId":"earlier"}]}
                """);
        upstream("subscriptionsv2/tokens/demo", subscription("SUBSCRIPTION_STATE_ACTIVE", "monthly", "2026-02-01T00:00:00Z"));
        assertThat(gateway.isVoided(PACKAGE, "demo")).isFalse();
        google.verify();
    }

    @Test
    void recognizesRefundForCurrentSubscriptionOrderOnLaterPage() {
        upstream("voidedpurchases?type=1", """
                {"tokenPagination":{"nextPageToken":"next+token=x"}}
                """);
        upstream("voidedpurchases?type=1&token=next%2Btoken%3Dx", """
                {"voidedPurchases":[{"purchaseToken":"demo","orderId":"latest"}]}
                """);
        upstream("subscriptionsv2/tokens/demo", subscription("SUBSCRIPTION_STATE_ACTIVE", "monthly", "2026-02-01T00:00:00Z"));
        assertThat(gateway.isVoided(PACKAGE, "demo")).isTrue();
        google.verify();
    }

    @Test
    void aDeferredLineDoesNotReplaceTheCurrentlyOwnedPlan() {
        String response = subscription("SUBSCRIPTION_STATE_ACTIVE", "monthly", "2026-02-01T00:00:00Z")
                .replace("}}]", """
                        }},{"productId":"controleja_plus","offerDetails":{"basePlanId":"yearly"},"expiryTime":"2027-02-01T00:00:00Z"}]
                        """);
        upstream("subscriptionsv2/tokens/demo", response);
        var result = gateway.verify(PACKAGE, PLUS, "demo");
        assertThat(result.active()).isTrue();
        assertThat(result.basePlanId()).isEqualTo("monthly");
        assertThat(result.orderId()).isEqualTo("latest");
        google.verify();
    }

    @Test
    void checksAnAlreadyVerifiedOrderWithoutAnotherPublisherLookup() {
        upstream("voidedpurchases?type=1", """
                {"voidedPurchases":[{"purchaseToken":"demo","orderId":"earlier"},{"purchaseToken":"other","orderId":"latest"}]}
                """);
        assertThat(gateway.isVoided(PACKAGE, "demo", "latest")).isFalse();
        google.verify();
    }

    @ParameterizedTest
    @CsvSource({"400,true", "404,true", "410,true", "401,false", "403,false", "429,false", "503,false"})
    void errorsDistinguishInvalidPurchaseAndNeverIncludeUpstreamBody(int status, boolean invalid) {
        google.expect(requestTo(BASE + "productsv2/tokens/sensitive-token"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("sensitive-upstream-body"));
        assertThatThrownBy(() -> gateway.verify(PACKAGE, ONE_TIME, "sensitive-token"))
                .isInstanceOfSatisfying(GooglePlayFailure.class, error -> {
                    assertThat(error.isInvalidPurchase()).isEqualTo(invalid);
                    assertThat(error.getMessage()).doesNotContain("sensitive");
                    assertThat(error.getCause()).isNull();
                });
        google.verify();
    }

    private void upstream(String path, String json) {
        google.expect(requestTo(BASE + path)).andExpect(header("Authorization", "Bearer mock-access"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String subscription(String state, String basePlan, String expiry) {
        return """
                {
                    "subscriptionState":"%s",
                    "startTime":"2025-12-31T00:00:00Z",
                    "linkedPurchaseToken":"previous",
                    "externalAccountIdentifiers":{"obfuscatedExternalAccountId":"account"},
                    "lineItems":[{"productId":"controleja_plus","offerDetails":{"basePlanId":"%s"},
                        "latestSuccessfulOrderId":"latest","expiryTime":"%s","autoRenewingPlan":{"autoRenewEnabled":true}}]
                }
                """.formatted(state, basePlan, expiry);
    }
}
