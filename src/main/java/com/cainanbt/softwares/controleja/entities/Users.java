package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
    @Column(nullable = false)
    private Boolean oauth2User;
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
