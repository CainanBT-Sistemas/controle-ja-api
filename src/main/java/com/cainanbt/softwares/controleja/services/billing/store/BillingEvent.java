package com.cainanbt.softwares.controleja.services.billing.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "billing_events")
@Getter
@Setter
@NoArgsConstructor
public class BillingEvent {
    @Id
    private String id;
    @Column(length = 64)
    private String fingerprint;
    @Column(nullable = false, length = 80)
    private String type;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "error_code", length = 80)
    private String errorCode;
}
