package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "installment_plan", indexes = {
        @Index(name = "idx_installments_invoice_user_deleted_date", columnList = "invoices_id, user_id, deletedAt, date"),
        @Index(name = "idx_installments_purchase_user_deleted", columnList = "purchaseId, user_id, deletedAt"),
        @Index(name = "idx_installments_user_date_deleted", columnList = "user_id, date, deletedAt"),
        @Index(name = "idx_installments_invoice_paid_amount", columnList = "invoices_id, paid, amount"),
        @Index(name = "idx_installments_advance_operation", columnList = "advanceOperationId, user_id, deletedAt")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
public class InstallmentPlan {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Long date;
    @Column(nullable = false)
    private String name;
    @Column(nullable = true)
    private String description;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private int totalInstallmentsPlan;
    @Column(nullable = false)
    private Integer currentInstallment;
    @Column(nullable = false)
    private Boolean fixed;
    @Column(nullable = false)
    private Boolean paid;
    @Column(nullable = false)
    private UUID purchaseId;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
    @Column(nullable = true)
    private UUID advanceOperationId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advanced_from_invoice_id", nullable = true)
    private Invoices advancedFromInvoice;
    @Column(nullable = true)
    private Long advanceCorrectedAt;
    @ManyToOne
    @JoinColumn(name = "invoices_id", nullable = false)
    private Invoices invoices;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;
}
