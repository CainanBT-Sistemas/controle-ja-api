package com.cainanbt.softwares.controleja.controller;

import com.cainanbt.softwares.controleja.services.billing.BillingProblem;
import com.cainanbt.softwares.controleja.services.billing.BillingService;
import com.cainanbt.softwares.controleja.services.billing.BillingService.PurchaseRequest;
import com.cainanbt.softwares.controleja.services.billing.BillingSettings;
import com.cainanbt.softwares.controleja.services.billing.PubSubAuthorization;
import com.cainanbt.softwares.controleja.utils.SecurityContextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping(BillingController.PATH)
@EnableConfigurationProperties(BillingSettings.class)
public class BillingController {
    public static final String PATH = "/controle_ja_api/v1/billing/google-play";
    public static final String NOTIFICATIONS = PATH + "/notifications";
    private static final int MAX_NOTIFICATION_BYTES = 65_536;

    private final BillingService billing;
    private final BillingSettings settings;
    private final PubSubAuthorization pubsub;
    private final ObjectMapper json;

    public BillingController(BillingService billing, BillingSettings settings, PubSubAuthorization pubsub, ObjectMapper json) {
        this.billing = billing;
        this.settings = settings;
        this.pubsub = pubsub;
        this.json = json;
    }

    @GetMapping("/account")
    public BillingAccount account() {
        return new BillingAccount(settings.packageName(), billing.accountId(SecurityContextUtils.getCurrentUser()));
    }

    @PostMapping("/verify")
    public BillingService.Result verify(@Valid @RequestBody BillingService.PurchaseRequest purchase) {
        return billing.verify(SecurityContextUtils.getCurrentUser(), purchase);
    }

    @PostMapping("/restore")
    public BillingService.Result restore(@Valid @RequestBody RestoreRequest request) {
        return billing.restore(SecurityContextUtils.getCurrentUser(), request.purchases());
    }

    @PostMapping("/notifications")
    public ResponseEntity<Void> notifications(HttpServletRequest request) {
        pubsub.requireAuthorized(request.getHeader("Authorization"));
        BillingService.Notification event = parse(request);
        if (event != null) billing.notify(event);
        return ResponseEntity.noContent().build();
    }

    private BillingService.Notification parse(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_NOTIFICATION_BYTES + 1);
            if (body.length > MAX_NOTIFICATION_BYTES) throw invalidNotification();
            JsonNode envelope = json.readTree(body);
            if (envelope == null || !envelope.isObject()) throw invalidNotification();
            JsonNode message = envelope.path("message");
            String id = requiredText(message, "messageId", 255);
            String data = requiredText(message, "data", MAX_NOTIFICATION_BYTES);
            JsonNode payload = json.readTree(Base64.getDecoder().decode(data));
            if (payload == null || !payload.isObject()) throw invalidNotification();
            String packageName = requiredText(payload, "packageName", 255);
            if (!settings.packageName().equals(packageName)) throw invalidNotification();
            JsonNode event;
            String product;
            String type;
            boolean voided = payload.hasNonNull("voidedPurchaseNotification");
            if (voided) {
                event = payload.path("voidedPurchaseNotification");
                product = switch (event.path("productType").asInt()) {
                    case 1 -> BillingSettings.PLUS;
                    case 2 -> BillingSettings.NO_ADS;
                    default -> throw invalidNotification();
                };
                type = "VOIDED_PURCHASE";
            } else if (payload.hasNonNull("subscriptionNotification")) {
                event = payload.path("subscriptionNotification");
                product = BillingSettings.PLUS;
                type = "SUBSCRIPTION";
            } else if (payload.hasNonNull("oneTimeProductNotification")) {
                event = payload.path("oneTimeProductNotification");
                product = requiredText(event, "sku", 255);
                if (!BillingSettings.NO_ADS.equals(product)) throw invalidNotification();
                type = "ONE_TIME_PRODUCT";
            } else if (payload.path("testNotification").isObject()) {
                return null;
            } else {
                throw invalidNotification();
            }
            String token = requiredText(event, "purchaseToken", 4096);
            return new BillingService.Notification(id, type,
                    new BillingService.PurchaseRequest(packageName, product, null, token), voided);
        } catch (IOException | IllegalArgumentException e) {
            throw invalidNotification();
        }
    }

    private static String requiredText(JsonNode node, String field, int max) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) {
            throw invalidNotification();
        }
        return value.textValue();
    }

    private static BillingProblem invalidNotification() {
        return new BillingProblem(HttpStatus.BAD_REQUEST, "BILLING_NOTIFICATION_INVALID", "Notificação inválida.");
    }

    public record BillingAccount(String packageName, String obfuscatedAccountId) {}
    public record RestoreRequest(@NotNull @Size(max = 10) List<@NotNull @Valid PurchaseRequest> purchases) {}
}
