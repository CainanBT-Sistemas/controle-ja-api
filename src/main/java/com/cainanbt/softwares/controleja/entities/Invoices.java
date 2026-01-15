package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "invoicess")
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
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transactions transaction;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;
}
