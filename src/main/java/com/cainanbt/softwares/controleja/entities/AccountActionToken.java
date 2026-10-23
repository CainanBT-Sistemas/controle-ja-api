package com.cainanbt.softwares.controleja.entities;

import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
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

import java.util.UUID;

@Entity
@Table(name = "account_action_tokens", indexes = {
        @Index(name = "idx_account_action_email_purpose_created", columnList = "emailHash,purpose,createdAt"),
        @Index(name = "idx_account_action_ip_created", columnList = "ipHash,createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountActionToken {
    @Id
    private UUID id;
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountActionPurpose purpose;
    @Column(nullable = false, length = 64)
    private String emailHash;
    @Column(nullable = false, length = 64)
    private String ipHash;
    @Column(nullable = false, length = 64)
    private String codeDigest;
    @Column(nullable = false)
    private Long expiresAt;
    @Column(nullable = false)
    private Integer attempts;
    @Column(nullable = false)
    private Integer maxAttempts;
    private Long consumedAt;
    private Long invalidatedAt;
    @Column(nullable = false)
    private Long createdAt;
    @Version
    private Long version;
}
