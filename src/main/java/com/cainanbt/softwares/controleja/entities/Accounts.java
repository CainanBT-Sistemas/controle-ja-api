package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.AccountType;
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
@Table(name = "accounts")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
public class Accounts {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String institution;
    @Column(nullable = false)
    private String currency;
    @Column(nullable = false)
    private BigDecimal currentBalance;
    @Column(nullable = false)
    private Boolean calculateBalance;
    @Column(nullable = false)
    private BigDecimal initialBalance;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private Users user;

    public void debit(BigDecimal amount) {
        // TODO validar saldo negativo
        this.currentBalance = this.currentBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.currentBalance = this.currentBalance.add(amount);
    }
}
