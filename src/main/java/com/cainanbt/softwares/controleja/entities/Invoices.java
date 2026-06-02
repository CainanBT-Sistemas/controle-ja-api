package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "invoicess", indexes = {
        @Index(name = "idx_invoices_user_expiration_deleted", columnList = "user_id, expirationDate, deletedAt"),
        @Index(name = "idx_invoices_user_card_expiration", columnList = "user_id, credit_card_id, expirationDate"),
        @Index(name = "idx_invoices_card_month_year", columnList = "credit_card_id, month, year"),
        @Index(name = "idx_invoices_user_paid_amount_expiration", columnList = "user_id, paid, amount, expirationDate")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
public class Invoices {
    @Id
    private UUID id;
    @Column(nullable = false)
    private Integer month;
    @Column(nullable = false)
    private Integer year;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private Long expirationDate;
    @Column(nullable = false)
    private  Boolean paid;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
    @ManyToOne
    @JoinColumn(name = "credit_card_id", nullable = false)
    private CreditCard creditCard;
    @ManyToOne
    @JoinColumn(name = "transaction_id", nullable = true)
    private Transactions transaction;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;
}
