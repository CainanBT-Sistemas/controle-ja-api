package com.cainanbt.softwares.controleja.services.billing;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import com.cainanbt.softwares.controleja.services.billing.google.GooglePlayFailure;
import com.cainanbt.softwares.controleja.services.billing.google.GooglePlayGateway;
import com.cainanbt.softwares.controleja.services.billing.google.PurchaseSnapshot;
import com.cainanbt.softwares.controleja.services.billing.store.BillingEvent;
import com.cainanbt.softwares.controleja.services.billing.store.BillingEventRepository;
import com.cainanbt.softwares.controleja.services.billing.store.BillingPurchase;
import com.cainanbt.softwares.controleja.services.billing.store.BillingPurchaseRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class BillingService {
    private final BillingSettings settings;
    private final BillingTokens tokens;
    private final GooglePlayGateway google;
    private final BillingPurchaseRepository purchases;
    private final BillingEventRepository events;
    private final UsersRepository users;
    private final EntitlementService entitlements;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public BillingService(BillingSettings settings, BillingTokens tokens, GooglePlayGateway google,
                          BillingPurchaseRepository purchases, BillingEventRepository events, UsersRepository users,
                          EntitlementService entitlements, JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.settings = settings;
        this.tokens = tokens;
        this.google = google;
        this.purchases = purchases;
        this.events = events;
        this.users = users;
        this.entitlements = entitlements;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public String accountId(Users user) {
        requireEnabled();
        return tokens.accountId(user.getId());
    }

    public Result verify(Users user, PurchaseRequest request) {
        requireEnabled();
        return new Result(List.of(verifyPurchase(user, request, false)), canonicalEntitlements(user));
    }

    public Result restore(Users user, List<PurchaseRequest> requests) {
        requireEnabled();
        Map<String, PurchaseRequest> candidates = new LinkedHashMap<>();
        for (PurchaseRequest request : requests) {
            candidates.putIfAbsent(BillingTokens.fingerprint(request.purchaseToken()), request);
        }
        // The store can omit purchases that were refunded while a notification was missed.
        for (BillingPurchase saved : purchases.findByUserIdAndActiveTrueAndSupersededFalse(user.getId())) {
            candidates.computeIfAbsent(saved.getFingerprint(), fingerprint -> new PurchaseRequest(
                    saved.getPackageName(), saved.getProductId(), null,
                    tokens.decrypt(saved.getEncryptedToken(), fingerprint)));
        }
        List<PurchaseResult> results = new ArrayList<>();
        for (PurchaseRequest request : candidates.values()) {
            try {
                results.add(verifyPurchase(user, request, true));
            } catch (BillingProblem problem) {
                if (problem.status().is5xxServerError()) throw problem;
                results.add(new PurchaseResult(request.productId(), request.basePlanId(), problem.code(), false, false));
            }
        }
        return new Result(results, canonicalEntitlements(user));
    }

    private PurchaseResult verifyPurchase(Users user, PurchaseRequest request, boolean reconcileVoids) {
        requireProduct(request.packageName(), request.productId());
        if (request.basePlanId() != null && (!BillingSettings.PLUS.equals(request.productId())
                || !BillingSettings.allowsBasePlan(request.basePlanId()))) {
            throw new BillingProblem(HttpStatus.BAD_REQUEST, "BILLING_PRODUCT_UNKNOWN", "Produto ou plano desconhecido.");
        }
        try {
            BillingPurchase saved = transactions.execute(tx -> {
                lock("google-play-purchase:", BillingTokens.fingerprint(request.purchaseToken()));
                lockUser(user.getId());
                return reconcile(user.getId(), request, reconcileVoids);
            });
            return result(acknowledge(saved));
        } catch (GooglePlayFailure failure) {
            if (failure.isInvalidPurchase()) throw BillingProblem.invalid();
            throw BillingProblem.temporary();
        } catch (ConcurrencyFailureException | DataIntegrityViolationException failure) {
            throw BillingProblem.temporary();
        }
    }

    public void notify(Notification notification) {
        requireEnabled();
        requireProduct(notification.purchase().packageName(), notification.purchase().productId());
        try {
            BillingPurchase saved = transactions.execute(tx -> {
                BillingEvent event = lockedEvent(notification);
                if ("PROCESSED".equals(event.getStatus())) return null;
                PurchaseRequest request = notification.purchase();
                lock("google-play-purchase:", event.getFingerprint());
                BillingPurchase known = purchases.findByFingerprint(event.getFingerprint()).orElse(null);
                UUID owner = known == null ? linkedOwner(request) : known.getUserId();
                BillingPurchase result = null;
                if (owner != null && users.lockById(owner).isPresent()) {
                    result = reconcile(owner, request, notification.voided());
                }
                event.setStatus(owner == null ? "PROCESSED" : "PENDING");
                event.setProcessedAt(owner == null ? Instant.now() : null);
                event.setErrorCode(null);
                events.save(event);
                return result;
            });
            acknowledge(saved);
            changeEventStatus(notification, "PROCESSED", null);
        } catch (GooglePlayFailure failure) {
            if (failure.isInvalidPurchase()) {
                changeEventStatus(notification, "PROCESSED", "BILLING_PURCHASE_INVALID");
                return;
            }
            changeEventStatus(notification, "FAILED", "BILLING_TEMPORARY_UNAVAILABLE");
            throw BillingProblem.temporary();
        } catch (BillingProblem failure) {
            changeEventStatus(notification, failure.status().is5xxServerError() ? "FAILED" : "PROCESSED", failure.code());
            if (failure.status().is5xxServerError()) throw failure;
        } catch (ConcurrencyFailureException | DataIntegrityViolationException failure) {
            throw BillingProblem.temporary();
        }
    }

    private UUID linkedOwner(PurchaseRequest request) {
        if (!BillingSettings.PLUS.equals(request.productId())) {
            google.verify(request.packageName(), request.productId(), request.purchaseToken());
            return null;
        }
        PurchaseSnapshot snapshot = google.verify(request.packageName(), request.productId(), request.purchaseToken());
        if (snapshot.linkedPurchaseToken() == null) return null;
        return purchases.findByFingerprint(BillingTokens.fingerprint(snapshot.linkedPurchaseToken()))
                .map(BillingPurchase::getUserId).orElse(null);
    }

    private BillingPurchase reconcile(UUID owner, PurchaseRequest request, boolean voidSignal) {
        String fingerprint = BillingTokens.fingerprint(request.purchaseToken());
        BillingPurchase previous = purchases.findByFingerprint(fingerprint).orElse(null);
        validateOwnership(owner, previous);
        if (previous != null && (!request.productId().equals(previous.getProductId())
                || !request.packageName().equals(previous.getPackageName()))) throw BillingProblem.invalid();
        PurchaseSnapshot official;
        try {
            official = google.verify(request.packageName(), request.productId(), request.purchaseToken());
        } catch (GooglePlayFailure failure) {
            if (previous == null || !failure.isInvalidPurchase()) throw failure;
            previous.setActive(false);
            previous.setOfficialState("INVALID");
            previous.setVerifiedAt(Instant.now());
            return purchases.saveAndFlush(previous);
        }
        boolean identityRequired = previous == null || official.active() || official.obfuscatedAccountId() != null;
        if (!request.productId().equals(official.productId())
                || (identityRequired && !tokens.accountId(owner).equals(official.obfuscatedAccountId()))
                || (official.subscription() && official.active() && !BillingSettings.allowsBasePlan(official.basePlanId()))
                || (official.active() && request.basePlanId() != null && !request.basePlanId().equals(official.basePlanId()))) {
            throw BillingProblem.invalid();
        }

        String linked = official.linkedPurchaseToken() == null ? null : BillingTokens.fingerprint(official.linkedPurchaseToken());
        if (fingerprint.equals(linked)) throw BillingProblem.invalid();
        BillingPurchase old = linked == null ? null : purchases.findByFingerprint(linked).orElse(null);
        validateOwnership(owner, old);

        Instant now = Instant.now();
        BillingPurchase current = previous == null ? new BillingPurchase() : previous;
        if (previous == null) {
            current.setUserId(owner);
            current.setFingerprint(fingerprint);
            current.setEncryptedToken(tokens.encrypt(request.purchaseToken(), fingerprint));
            current.setCreatedAt(now);
        }
        String previousOrder = previous == null ? null : previous.getOrderId();
        String currentOrder = official.orderId() == null ? previousOrder : official.orderId();
        boolean sameOrder = previous != null && (!official.subscription() || previousOrder == null
                || Objects.equals(previousOrder, currentOrder));
        boolean priorVoid = sameOrder && "VOIDED".equals(current.getOfficialState());
        boolean requireVoidCheck = voidSignal || (official.active() && (previous == null || !sameOrder));
        boolean voided = priorVoid || (requireVoidCheck
                && google.isVoided(request.packageName(), request.purchaseToken(), currentOrder));

        current.setPackageName(request.packageName());
        current.setProductId(official.productId());
        current.setBasePlanId(official.basePlanId());
        current.setOfficialState(voided ? "VOIDED" : official.state());
        current.setSubscription(official.subscription());
        current.setActive(official.active() && !voided && !current.isSuperseded());
        current.setAcknowledged(current.isAcknowledged() || official.acknowledged());
        current.setStartsAt(official.startsAt());
        current.setExpiresAt(official.expiresAt());
        current.setVerifiedAt(now);
        current.setAutoRenew(official.autoRenew());
        current.setLinkedFingerprint(linked);
        current.setOrderId(currentOrder);
        if (linked != null && official.active() && !voided) {
            if (old == null) {
                old = new BillingPurchase();
                old.setUserId(owner);
                old.setFingerprint(linked);
                old.setEncryptedToken(tokens.encrypt(official.linkedPurchaseToken(), linked));
                old.setPackageName(request.packageName());
                old.setProductId(official.productId());
                old.setSubscription(true);
                old.setOfficialState("SUPERSEDED");
                old.setVerifiedAt(now);
                old.setCreatedAt(now);
            }
            old.setSuperseded(true);
            old.setActive(false);
            purchases.save(old);
        }
        return purchases.saveAndFlush(current);
    }

    private BillingPurchase acknowledge(BillingPurchase saved) {
        if (saved == null || saved.isAcknowledged() || !saved.isActive() || saved.isSuperseded()) return saved;
        return transactions.execute(tx -> {
            lock("google-play-purchase:", saved.getFingerprint());
            lockUser(saved.getUserId());
            BillingPurchase current = purchases.findByFingerprint(saved.getFingerprint()).orElseThrow(BillingProblem::invalid);
            if (current.isAcknowledged() || !current.isActive() || current.isSuperseded()) return current;
            try {
                google.acknowledge(current.getPackageName(), current.getProductId(),
                        tokens.decrypt(current.getEncryptedToken(), current.getFingerprint()), current.isSubscription());
            } catch (GooglePlayFailure failure) {
                throw BillingProblem.temporary();
            }
            current.setAcknowledged(true);
            return purchases.saveAndFlush(current);
        });
    }

    private BillingEvent lockedEvent(Notification notification) {
        // Serialize deliveries of the same Pub/Sub message before inserting its unique ID.
        lock("google-play-rtdn:", notification.id());
        return events.findById(notification.id()).orElseGet(() -> {
            BillingEvent event = new BillingEvent();
            event.setId(notification.id());
            event.setFingerprint(BillingTokens.fingerprint(notification.purchase().purchaseToken()));
            event.setType(notification.type());
            event.setReceivedAt(Instant.now());
            return event;
        });
    }

    private void lock(String scope, String id) {
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (PreparedStatementCallback<Void>) statement -> {
            statement.setString(1, scope + id);
            statement.execute();
            return null;
        });
    }

    private void changeEventStatus(Notification notification, String status, String code) {
        transactions.executeWithoutResult(tx -> {
            BillingEvent event = lockedEvent(notification);
            if ("PROCESSED".equals(event.getStatus())) return;
            event.setStatus(status);
            event.setErrorCode(code);
            event.setProcessedAt("PROCESSED".equals(status) ? Instant.now() : null);
            events.save(event);
        });
    }

    private UserEntitlementsDTO canonicalEntitlements(Users principal) {
        return transactions.execute(tx -> {
            lockUser(principal.getId());
            return entitlements.buildForUser(users.findById(principal.getId()).orElseThrow(BillingProblem::invalid));
        });
    }

    private void lockUser(UUID userId) {
        if (users.lockById(userId).isEmpty()) throw BillingProblem.invalid();
    }

    private static void validateOwnership(UUID userId, BillingPurchase existing) {
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new BillingProblem(HttpStatus.CONFLICT, "BILLING_TOKEN_ALREADY_LINKED", "Compra já vinculada a outra conta.");
        }
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw BillingProblem.disabled();
    }

    private void requireProduct(String packageName, String productId) {
        if (!settings.allows(packageName, productId)) {
            throw new BillingProblem(HttpStatus.BAD_REQUEST, "BILLING_PRODUCT_UNKNOWN", "Produto ou pacote desconhecido.");
        }
    }

    private static PurchaseResult result(BillingPurchase purchase) {
        return new PurchaseResult(purchase.getProductId(), purchase.getBasePlanId(), purchase.getOfficialState(),
                purchase.isActive(), purchase.isAcknowledged());
    }

    public record PurchaseRequest(@NotBlank @Size(max = 255) String packageName,
                                  @NotBlank @Size(max = 255) String productId,
                                  @Size(max = 64) String basePlanId,
                                  @NotBlank @Size(max = 4096) String purchaseToken) {
        @Override public String toString() { return "PurchaseRequest[redacted]"; }
    }

    public record Notification(String id, String type, PurchaseRequest purchase, boolean voided) {}
    public record PurchaseResult(String productId, String basePlanId, String state, boolean active, boolean acknowledged) {}
    public record Result(List<PurchaseResult> purchases, UserEntitlementsDTO entitlements) {}
}
