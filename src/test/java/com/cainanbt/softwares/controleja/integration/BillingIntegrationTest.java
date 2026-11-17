package com.cainanbt.softwares.controleja.integration;

import com.cainanbt.softwares.controleja.config.BaseTest;
import com.cainanbt.softwares.controleja.services.billing.PubSubAuthorization;
import com.cainanbt.softwares.controleja.services.billing.google.GooglePlayFailure;
import com.cainanbt.softwares.controleja.services.billing.google.GooglePlayGateway;
import com.cainanbt.softwares.controleja.services.billing.google.PurchaseSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "app.billing.google-play.enabled=true",
        "app.billing.google-play.token-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.billing.google-play.pubsub-audience=https://billing.test/rtdn",
        "app.billing.google-play.pubsub-service-account=pubsub@billing.iam.gserviceaccount.com"
})
class BillingIntegrationTest extends BaseTest {
    private static final String ROUTE = "/billing/google-play";
    private static final String PACKAGE = "br.com.controleja.app";
    private static final String PLUS = "controleja_plus";
    private static final String NO_ADS = "controleja_no_ads_lifetime";

    @MockBean private GooglePlayGateway google;
    @SpyBean private PubSubAuthorization pubsub;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void usesOfficialOwnershipAndPreservesLifetimeRightAfterSubscriptionEnds() {
        Session owner = signIn();
        Session other = signIn();
        String lifetime = UUID.randomUUID().toString();
        String subscription = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, NO_ADS, lifetime))
                .thenReturn(snapshot(NO_ADS, owner, "PURCHASED", true, true, null));
        when(google.verify(PACKAGE, PLUS, subscription))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ACTIVE", true, true, null));

        verifyPurchase(owner, buy(NO_ADS, lifetime)).statusCode(200)
                .body("entitlements.plan", is("FREE_NO_ADS")).body("entitlements.limits.creditCards", is(2));
        given().auth().oauth2(owner.jwt()).contentType(ContentType.JSON)
                .body(Map.of("purchases", List.of(buy(NO_ADS, lifetime), buy(PLUS, subscription))))
                .post(ROUTE + "/restore").then().statusCode(200).body("entitlements.plan", is("PLUS"))
                .body(not(containsString(lifetime))).body(not(containsString(subscription)));
        verifyPurchase(other, buy(PLUS, subscription)).statusCode(409)
                .body("title", is("BILLING_TOKEN_ALREADY_LINKED"));

        when(google.verify(PACKAGE, PLUS, subscription))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_EXPIRED", false, true, null));
        verifyPurchase(owner, buy(PLUS, subscription)).statusCode(200)
                .body("entitlements.plan", is("FREE_NO_ADS"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM billing_purchases WHERE user_id = ?",
                Integer.class, owner.id()));
    }

    @Test
    void savesBeforeAcknowledgementAndRetriesWithoutCreatingAnotherPurchase() {
        Session owner = signIn();
        String token = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, NO_ADS, token))
                .thenReturn(snapshot(NO_ADS, owner, "PURCHASED", true, false, null));
        doThrow(new GooglePlayFailure(false)).doNothing().when(google).acknowledge(PACKAGE, NO_ADS, token, false);

        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(503).body(not(containsString(token)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM billing_purchases WHERE user_id = ?", Integer.class, owner.id()));
        assertEquals(false, jdbc.queryForObject("SELECT acknowledged FROM billing_purchases WHERE user_id = ?", Boolean.class, owner.id()));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200)
                .body("purchases[0].acknowledged", is(true)).body("entitlements.plan", is("FREE_NO_ADS"));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200);
        verify(google, times(2)).acknowledge(PACKAGE, NO_ADS, token, false);
    }

    @Test
    void pendingAndWrongPurchaseOwnersNeverReceiveRights() {
        Session owner = signIn();
        Session other = signIn();
        String token = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, NO_ADS, token))
                .thenReturn(snapshot(NO_ADS, owner, "PENDING", false, false, null));
        verifyPurchase(other, buy(NO_ADS, token)).statusCode(400).body(not(containsString(token)));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200)
                .body("purchases[0].state", is("PENDING"))
                .body("entitlements.plan", is("FREE"));
        verifyPurchase(owner, Map.of("packageName", "wrong.package", "productId", NO_ADS, "purchaseToken", token))
                .statusCode(400).body("title", is("BILLING_PRODUCT_UNKNOWN"));
    }

    @Test
    void processesOnlyAuthenticatedNotificationAndDeduplicatesItsOfficialLookup() throws Exception {
        Session owner = signIn();
        String token = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, NO_ADS, token))
                .thenReturn(snapshot(NO_ADS, owner, "PURCHASED", true, true, null));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200);
        Map<String, Object> event = notification(token);
        given().contentType(ContentType.JSON).auth().oauth2(owner.jwt()).body(event)
                .post(ROUTE + "/notifications").then().statusCode(401);
        doNothing().when(pubsub).requireAuthorized("Bearer signed-pubsub-fixture");
        when(google.isVoided(PACKAGE, token, "test-order")).thenReturn(true);
        for (int i = 0; i < 2; i++) {
            given().contentType(ContentType.JSON).auth().oauth2("signed-pubsub-fixture").body(event)
                    .post(ROUTE + "/notifications").then().statusCode(204);
        }
        verify(google, times(2)).verify(PACKAGE, NO_ADS, token);
        given().auth().oauth2(owner.jwt()).get("/users/me/entitlements").then().statusCode(200)
                .body("plan", is("FREE"));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200)
                .body("purchases[0].state", is("VOIDED")).body("entitlements.plan", is("FREE"));
        given().contentType(ContentType.JSON).auth().oauth2("signed-pubsub-fixture").body("{}")
                .post(ROUTE + "/notifications").then().statusCode(400);
    }

    @Test
    void restoresAnEmptyStoreListAndRevokesALifetimePurchaseAfterAMissedNotification() {
        Session owner = signIn();
        String token = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, NO_ADS, token))
                .thenReturn(snapshot(NO_ADS, owner, "PURCHASED", true, true, null));
        verifyPurchase(owner, buy(NO_ADS, token)).statusCode(200).body("entitlements.plan", is("FREE_NO_ADS"));

        when(google.isVoided(PACKAGE, token, "test-order")).thenReturn(true);
        given().auth().oauth2(owner.jwt()).contentType(ContentType.JSON)
                .body(Map.of("purchases", List.of())).post(ROUTE + "/restore").then().statusCode(200)
                .body("entitlements.plan", is("FREE")).body("purchases[0].state", is("VOIDED"))
                .body(not(containsString(token)));
    }

    @Test
    void renewalCanRestorePlusAfterAnOmittedOrderIdOnARefundedCycle() throws Exception {
        Session owner = signIn();
        String token = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, PLUS, token))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ACTIVE", true, true, null));
        verifyPurchase(owner, buy(PLUS, token)).statusCode(200).body("entitlements.plan", is("PLUS"));

        doNothing().when(pubsub).requireAuthorized("Bearer signed-pubsub-fixture");
        when(google.isVoided(PACKAGE, token, "test-order")).thenReturn(true);
        given().contentType(ContentType.JSON).auth().oauth2("signed-pubsub-fixture")
                .body(notification(token, 1)).post(ROUTE + "/notifications").then().statusCode(204);

        when(google.verify(PACKAGE, PLUS, token))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ON_HOLD", false, true, null, null));
        verifyPurchase(owner, buy(PLUS, token)).statusCode(200).body("entitlements.plan", is("FREE"));
        when(google.verify(PACKAGE, PLUS, token))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ACTIVE", true, true, null, "renewal-order"));
        verifyPurchase(owner, buy(PLUS, token)).statusCode(200).body("entitlements.plan", is("PLUS"));
    }

    @Test
    void supersedesLinkedSubscriptionAndSerializesConcurrentVerification() throws Exception {
        Session owner = signIn();
        String older = UUID.randomUUID().toString();
        String newer = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, PLUS, older))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ACTIVE", true, true, null));
        when(google.verify(PACKAGE, PLUS, newer))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", true, true, older));
        verifyPurchase(owner, buy(PLUS, older)).statusCode(200);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); verifyPurchase(owner, buy(PLUS, newer)).statusCode(200); return true; });
            var second = executor.submit(() -> { start.await(); verifyPurchase(owner, buy(PLUS, newer)).statusCode(200); return true; });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        verifyPurchase(owner, buy(PLUS, older)).statusCode(200).body("purchases[0].active", is(false));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM billing_purchases WHERE user_id = ? AND active",
                Integer.class, owner.id()));
    }

    @Test
    void cannotRestoreAnUnseenPredecessorAfterTheReplacementWasActivated() {
        Session owner = signIn();
        String older = UUID.randomUUID().toString();
        String newer = UUID.randomUUID().toString();
        when(google.verify(PACKAGE, PLUS, newer))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_ACTIVE", true, true, older));
        when(google.verify(PACKAGE, PLUS, older))
                .thenReturn(snapshot(PLUS, owner, "SUBSCRIPTION_STATE_CANCELED", true, true, null));

        verifyPurchase(owner, buy(PLUS, newer)).statusCode(200).body("entitlements.plan", is("PLUS"));
        verifyPurchase(owner, buy(PLUS, older)).statusCode(200).body("purchases[0].active", is(false));
    }

    private Session signIn() {
        String email = "billing-" + UUID.randomUUID() + "@test.local";
        String password = "test-password-123";
        String id = given().contentType(ContentType.JSON)
                .body(Map.of("username", "Billing Test", "email", email, "password", password))
                .post("/users/register").then().statusCode(200).extract().path("id");
        verifyEmail(email);
        String jwt = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .post("/auth").then().statusCode(200).extract().path("tokens.accessToken");
        String obfuscated = given().auth().oauth2(jwt).get(ROUTE + "/account")
                .then().statusCode(200).extract().path("obfuscatedAccountId");
        return new Session(UUID.fromString(id), jwt, obfuscated);
    }

    private ValidatableResponse verifyPurchase(Session user, Map<String, String> request) {
        return given().auth().oauth2(user.jwt()).contentType(ContentType.JSON).body(request).post(ROUTE + "/verify").then();
    }

    private static Map<String, String> buy(String product, String token) {
        return Map.of("packageName", PACKAGE, "productId", product, "purchaseToken", token);
    }

    private static PurchaseSnapshot snapshot(String product, Session user, String state, boolean active,
                                               boolean acknowledged, String linked) {
        return snapshot(product, user, state, active, acknowledged, linked, "test-order");
    }

    private static PurchaseSnapshot snapshot(String product, Session user, String state, boolean active,
                                               boolean acknowledged, String linked, String orderId) {
        boolean subscription = PLUS.equals(product);
        return new PurchaseSnapshot(product, subscription ? "monthly" : null, user.obfuscated(), state,
                active, acknowledged, Instant.now().minusSeconds(60),
                subscription ? Instant.now().plusSeconds(3600) : null, subscription, linked, subscription, orderId);
    }

    private Map<String, Object> notification(String token) throws Exception {
        return notification(token, 2);
    }

    private Map<String, Object> notification(String token, int productType) throws Exception {
        byte[] payload = json.writeValueAsBytes(Map.of("packageName", PACKAGE,
                "voidedPurchaseNotification", Map.of("purchaseToken", token, "productType", productType)));
        return Map.of("message", Map.of("messageId", UUID.randomUUID().toString(),
                "data", Base64.getEncoder().encodeToString(payload)));
    }

    private record Session(UUID id, String jwt, String obfuscated) {}
}
