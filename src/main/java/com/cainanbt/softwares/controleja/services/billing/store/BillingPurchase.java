package com.cainanbt.softwares.controleja.services.billing.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_purchases",
        uniqueConstraints = @UniqueConstraint(name = "uq_billing_purchases_fingerprint", columnNames = "fingerprint"),
        indexes = @Index(name = "idx_billing_purchases_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class BillingPurchase {
    @Id
    private UUID id = UUID.randomUUID();
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @JsonIgnore
    @Column(name = "encrypted_token", nullable = false, columnDefinition = "bytea")
    private byte[] encryptedToken;
    @Column(name = "package_name", nullable = false)
    private String packageName;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(name = "base_plan_id")
    private String basePlanId;
    @Column(name = "order_id")
    private String orderId;
    @Column(name = "official_state", nullable = false, length = 64)
    private String officialState;
    @Column(nullable = false)
    private boolean subscription;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean acknowledged;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;
    @Column(name = "auto_renew")
    private Boolean autoRenew;
    @Column(name = "linked_fingerprint", length = 64)
    private String linkedFingerprint;
    @Column(nullable = false)
    private boolean superseded;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
