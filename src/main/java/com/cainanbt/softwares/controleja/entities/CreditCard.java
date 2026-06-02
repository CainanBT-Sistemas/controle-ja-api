package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
@Table(name = "credit_cards", indexes = {
        @Index(name = "idx_credit_cards_user_deleted", columnList = "user_id, deletedAt"),
        @Index(name = "idx_credit_cards_account_deleted", columnList = "account_id, deletedAt")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString
@SQLDelete(sql = "UPDATE credit_cards SET deleted_at = EXTRACT(EPOCH FROM NOW()) * 1000 WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class CreditCard {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = true)
    private String description;
    @Column(nullable = false)
    private BigDecimal totalLimit;
    @Column(nullable = false)
    private BigDecimal currentLimit;
    @Column(nullable = false)
    private int closeDay;
    @Column(nullable = false)
    private int bestDay;
    @Column(name = "icon")
    private String icon;
    @Column(name = "color")
    private String color;
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
    @OneToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Accounts accounts;

    /**
     * Consome limite disponível ao lançar compra no cartão.
     */
    public void consumeLimit(BigDecimal amount) {
        if (this.currentLimit.compareTo(amount) < 0) {
            throw new BadRequestException("Erro", "Limite insuficiente.");
        }
        this.currentLimit = this.currentLimit.subtract(amount);
    }

    /**
     * Devolve limite disponível sem ultrapassar o limite total do cartão.
     */
    public void restoreLimit(BigDecimal amount) {
        this.currentLimit = this.currentLimit.add(amount);
        if (this.currentLimit.compareTo(this.totalLimit) > 0) {
            this.currentLimit = this.totalLimit;
        }
    }
}
