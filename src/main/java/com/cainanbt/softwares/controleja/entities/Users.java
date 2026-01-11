package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Setter
@ToString(exclude = {"password", "refreshToken"})
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
