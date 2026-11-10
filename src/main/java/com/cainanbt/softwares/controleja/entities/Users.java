package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.EffectivePlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email_deleted", columnList = "email, deletedAt"),
        @Index(name = "idx_users_enabled_locked_deleted", columnList = "enabled, accountNonLocked, deletedAt")
})
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString(exclude = {"password", "refreshToken"})
@SQLDelete(sql = "UPDATE users SET deleted_at = EXTRACT(EPOCH FROM NOW()) * 1000 WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Users {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private Boolean enabled;
    @Column(nullable = false)
    private Boolean accountNonExpired;
    @Column(nullable = false)
    private Boolean accountNonLocked;
    @Column(nullable = false)
    private Boolean credentialsNonExpired;
    @Column(nullable = false)
    private String role;
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EffectivePlan plan = EffectivePlan.FREE;
    @Builder.Default
    @Version
    @Column(name = "entitlement_version", nullable = false)
    private Long entitlementVersion = 0L;
    @Builder.Default
    @Column(name = "entitlement_touched_at", nullable = false)
    private Long entitlementTouchedAt = 0L;
    @Column(nullable = false)
    private Boolean oauth2User;
    @Column(nullable = false)
    @Builder.Default
    private Boolean localPasswordEnabled = true;
    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;
    @Column(nullable = false)
    @Builder.Default
    private Long sessionVersion = 0L;
    @Column(nullable = true, columnDefinition = "TEXT")
    private String oauth2Provider;
    @Column(nullable = true, columnDefinition = "TEXT")
    private String oauth2ProviderId;
    @Column(nullable = true, columnDefinition = "TEXT")
    private String refreshToken;
    @Column(nullable = true)
    private long refreshTokenExpiry;
    @Column(nullable = true)
    private String lastIp;
    @Column(nullable = true)
    private String LastUserAgent;
    @Column(nullable = false)
    private Long createdAt;
    @Column(nullable = true)
    private Long updatedAt;
    @Column(nullable = true)
    private Long deletedAt;
}
