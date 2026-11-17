package com.cainanbt.softwares.controleja.services.billing.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component
public class GooglePlayGateway {
    private static final URI OAUTH_URL = URI.create("https://oauth2.googleapis.com/token");
    private static final String PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String PUBLISHER_URL = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";
    private static final String NO_ADS = "controleja_no_ads_lifetime";
    private static final String PLUS = "controleja_plus";
    private static final String ACKNOWLEDGED = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";
    private static final Set<String> ENTITLED_SUBSCRIPTION_STATES = Set.of(
            "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED");

    private final ObjectMapper mapper;
    private final String serviceAccountJson;
    private final ClientHttpRequestFactory transport;
    private final Clock clock;
    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    @Autowired
    public GooglePlayGateway(ObjectMapper mapper,
                             @Value("${app.billing.google-play.service-account-json:}") String serviceAccountJson) {
        this(mapper, serviceAccountJson, timedTransport(), Clock.systemUTC());
    }

    GooglePlayGateway(ObjectMapper mapper, String serviceAccountJson, ClientHttpRequestFactory transport, Clock clock) {
        this.mapper = mapper;
        this.serviceAccountJson = serviceAccountJson;
        this.transport = transport;
        this.clock = clock;
    }

    public PurchaseSnapshot verify(String packageName, String productId, String token) {
        return switch (productId) {
            case NO_ADS -> product(packageName, token);
            case PLUS -> subscription(packageName, token);
            default -> throw new GooglePlayFailure(true);
        };
    }

    public void acknowledge(String packageName, String productId, String token, boolean subscription) {
        if (!(subscription ? PLUS : NO_ADS).equals(productId)) {
            throw new GooglePlayFailure(true);
        }
        URI url = endpoint(packageName)
                .pathSegment(subscription ? "subscriptions" : "products", productId, "tokens", "{token}:acknowledge")
                .encode().buildAndExpand(token).toUri();
        send(HttpMethod.POST, url, MediaType.APPLICATION_JSON, "{}", oauthToken(), false, false);
    }

    public boolean isVoided(String packageName, String token) {
        return isVoided(packageName, token, null, true);
    }

    public boolean isVoided(String packageName, String token, String orderId) {
        return isVoided(packageName, token, orderId, false);
    }

    private boolean isVoided(String packageName, String token, String orderId, boolean resolveCurrentOrder) {
        Set<String> visited = new HashSet<>();
        String cursor = null;
        do {
            var url = endpoint(packageName).pathSegment("voidedpurchases").queryParam("type", 1);
            URI page = cursor == null ? url.build().toUri()
                    : url.queryParam("token", "{cursor}").encode().buildAndExpand(cursor).toUri();
            JsonNode response = read(page, false);
            for (JsonNode item : response.path("voidedPurchases")) {
                if (!token.equals(string(item, "purchaseToken"))) {
                    continue;
                }
                if (resolveCurrentOrder) {
                    try {
                        orderId = subscription(packageName, token).orderId();
                    } catch (GooglePlayFailure failure) {
                        if (!failure.isInvalidPurchase()) {
                            throw failure;
                        }
                        return true;
                    }
                    resolveCurrentOrder = false;
                }
                if (orderId == null || orderId.equals(string(item, "orderId"))) {
                    return true;
                }
            }
            cursor = string(response.path("tokenPagination"), "nextPageToken");
            if (cursor != null && !visited.add(cursor)) {
                throw new GooglePlayFailure(false);
            }
        } while (cursor != null);
        return false;
    }

    private PurchaseSnapshot product(String packageName, String token) {
        URI url = purchaseUrl(packageName, "productsv2", token);
        JsonNode result = read(url, true);
        for (JsonNode line : result.path("productLineItem")) {
            if (!NO_ADS.equals(string(line, "productId"))) {
                continue;
            }
            String state = state(result.path("purchaseStateContext"), "purchaseState");
            JsonNode offer = line.path("productOfferDetails");
            boolean active = "PURCHASED".equals(state)
                    && !"CONSUMPTION_STATE_CONSUMED".equals(string(offer, "consumptionState"))
                    && (!offer.hasNonNull("refundableQuantity") || offer.path("refundableQuantity").asInt() > 0)
                    && !offer.hasNonNull("rentOfferDetails");
            return new PurchaseSnapshot(NO_ADS, null, string(result, "obfuscatedExternalAccountId"), state,
                    active, ACKNOWLEDGED.equals(string(result, "acknowledgementState")),
                    timestamp(result, "purchaseCompletionTime"), null, null, null, false, string(result, "orderId"));
        }
        throw new GooglePlayFailure(true);
    }

    private PurchaseSnapshot subscription(String packageName, String token) {
        URI url = purchaseUrl(packageName, "subscriptionsv2", token);
        JsonNode result = read(url, true);
        JsonNode selected = null;
        Instant expiresAt = null;
        String orderId = null;
        for (JsonNode line : result.path("lineItems")) {
            if (!PLUS.equals(string(line, "productId"))) {
                continue;
            }
            Instant expiry = timestamp(line, "expiryTime");
            String lineOrder = string(line, "latestSuccessfulOrderId");
            boolean hasOrder = lineOrder != null;
            boolean selectedHasOrder = orderId != null;
            boolean laterExpiry = expiry != null && (expiresAt == null || expiry.isAfter(expiresAt));
            // A deferred replacement can advertise an expiry before it belongs to the purchaser.
            if (selected == null || (hasOrder && !selectedHasOrder) || (hasOrder == selectedHasOrder && laterExpiry)) {
                selected = line;
                expiresAt = expiry;
                orderId = lineOrder;
            }
        }
        if (selected == null) {
            throw new GooglePlayFailure(true);
        }
        String state = state(result, "subscriptionState");
        JsonNode renew = selected.path("autoRenewingPlan").path("autoRenewEnabled");
        boolean active = ENTITLED_SUBSCRIPTION_STATES.contains(state)
                && orderId != null && expiresAt != null && expiresAt.isAfter(clock.instant());
        return new PurchaseSnapshot(PLUS, string(selected.path("offerDetails"), "basePlanId"),
                string(result.path("externalAccountIdentifiers"), "obfuscatedExternalAccountId"), state,
                active, ACKNOWLEDGED.equals(string(result, "acknowledgementState")), timestamp(result, "startTime"),
                expiresAt, renew.isBoolean() ? renew.booleanValue() : null, string(result, "linkedPurchaseToken"),
                true, orderId);
    }

    private JsonNode read(URI url, boolean tokenLookup) {
        return send(HttpMethod.GET, url, null, null, oauthToken(), tokenLookup, true);
    }

    private synchronized String oauthToken() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(refreshAt)) {
            return accessToken;
        }
        String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="
                + URLEncoder.encode(assertion(now), StandardCharsets.UTF_8);
        JsonNode response = send(HttpMethod.POST, OAUTH_URL, MediaType.APPLICATION_FORM_URLENCODED,
                form, null, false, true);
        String token = string(response, "access_token");
        long expiresIn = response.path("expires_in").asLong();
        if (token == null || expiresIn <= 0 || expiresIn > 3600) {
            throw new GooglePlayFailure(false);
        }
        accessToken = token;
        refreshAt = now.plusSeconds(Math.max(0, expiresIn - 30));
        return accessToken;
    }

    private String assertion(Instant now) {
        if (serviceAccountJson.isBlank()) {
            throw new GooglePlayFailure(false);
        }
        try {
            JsonNode account = mapper.readTree(serviceAccountJson);
            String email = string(account, "client_email");
            String pem = string(account, "private_key");
            if (email == null || pem == null) {
                throw new GooglePlayFailure(false);
            }
            byte[] pkcs8 = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT);
            String keyId = string(account, "private_key_id");
            if (keyId != null) {
                header.keyID(keyId);
            }
            var claims = new JWTClaimsSet.Builder().issuer(email).audience(OAUTH_URL.toString())
                    .claim("scope", PLAY_SCOPE).issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600))).build();
            var jwt = new SignedJWT(header.build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (IOException | GeneralSecurityException | JOSEException | IllegalArgumentException exception) {
            throw new GooglePlayFailure(false);
        }
    }

    private JsonNode send(HttpMethod method, URI url, MediaType type, String body, String bearer,
                          boolean tokenLookup, boolean expectJson) {
        try {
            var request = transport.createRequest(url, method);
            if (bearer != null) {
                request.getHeaders().setBearerAuth(bearer);
            }
            if (body != null) {
                request.getHeaders().setContentType(type);
                request.getBody().write(body.getBytes(StandardCharsets.UTF_8));
            }
            try (ClientHttpResponse response = request.execute()) {
                int code = response.getStatusCode().value();
                if (code < 200 || code >= 300) {
                    if (code == 401 && bearer != null) {
                        invalidateToken(bearer);
                    }
                    throw new GooglePlayFailure(tokenLookup && (code == 400 || code == 404 || code == 410));
                }
                if (!expectJson) {
                    return null;
                }
                JsonNode result = mapper.readTree(response.getBody());
                if (result == null || !result.isObject()) {
                    throw new GooglePlayFailure(false);
                }
                return result;
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new GooglePlayFailure(false);
        }
    }

    private static UriComponentsBuilder endpoint(String packageName) {
        return UriComponentsBuilder.fromUriString(PUBLISHER_URL).pathSegment(packageName, "purchases");
    }

    private static URI purchaseUrl(String packageName, String kind, String token) {
        if (".".equals(token) || "..".equals(token)) {
            throw new GooglePlayFailure(true);
        }
        return endpoint(packageName).pathSegment(kind, "tokens", "{token}").encode().buildAndExpand(token).toUri();
    }

    private synchronized void invalidateToken(String rejected) {
        if (rejected.equals(accessToken)) {
            accessToken = null;
        }
    }

    private static String string(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static String state(JsonNode node, String field) {
        String value = string(node, field);
        return value == null ? "UNKNOWN" : value;
    }

    private static Instant timestamp(JsonNode node, String field) {
        String value = string(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GooglePlayFailure(false);
        }
    }

    private static ClientHttpRequestFactory timedTransport() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return factory;
    }
}
