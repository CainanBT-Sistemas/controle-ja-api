package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.FuelType;
import com.cainanbt.softwares.controleja.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "transactions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
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
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
    @ManyToOne
    @JoinColumn(name = "account_id",nullable = false)
    private Accounts account;
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;

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
}
