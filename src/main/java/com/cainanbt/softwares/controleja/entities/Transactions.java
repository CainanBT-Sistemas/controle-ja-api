package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.DrivingPredominance;
import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_user_deleted_date", columnList = "user_id, deletedAt, date"),
        @Index(name = "idx_transactions_user_type_paid_date", columnList = "user_id, type, paid, date"),
        @Index(name = "idx_transactions_recurrence_paid_date", columnList = "recurrence_rule_id, paid, date"),
        @Index(name = "idx_transactions_parent_deleted", columnList = "parent_transaction_id, deletedAt"),
        @Index(name = "idx_transactions_vehicle_date", columnList = "vehicle_id, date"),
        @Index(name = "idx_transactions_credit_card_date", columnList = "credit_card_id, date"),
        @Index(name = "idx_transactions_target_invoice", columnList = "target_invoice_id")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
@SQLDelete(sql = "UPDATE transactions SET deleted_at = EXTRACT(EPOCH FROM NOW()) * 1000 WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Transactions {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Long date;
    @Column(nullable = false)
    private String name;
    @Column(nullable = true)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private Boolean fixed;
    @Column(nullable = false)
    private Boolean paid;
    @ManyToOne
    @JoinColumn(name = "parent_transaction_id", nullable = true)
    private Transactions parentTransaction;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_rule_id", nullable = true)
    private RecurrenceRule recurrenceRule;
    @ManyToOne
    @JoinColumn(name = "account_id",nullable = false)
    private Accounts account;
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;
    //VEHICLE
    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = true)
    private Vehicle vehicle;
    @Column(nullable = true)
    private BigDecimal currentOdometer;
    @Column(nullable = true)
    private Double liters;
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private FuelType fuelType;
    @Column(nullable = true)
    private Double efficiency; // Km/L deste abastecimento específico
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private DrivingPredominance drivingPredominance;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gas_station_id", nullable = true)
    private GasStation gasStation;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_invoice_id", nullable = true)
    private Invoices targetInvoice;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id", nullable = true)
    private CreditCard creditCard;
}
