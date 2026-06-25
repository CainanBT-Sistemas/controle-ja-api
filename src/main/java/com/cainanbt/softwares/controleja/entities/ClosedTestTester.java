package com.cainanbt.softwares.controleja.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "closed_test_testers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_closed_test_testers_normalized_email",
                        columnNames = "normalized_email"
                )
        },
        indexes = {
                @Index(
                        name = "idx_closed_test_testers_normalized_enabled",
                        columnList = "normalized_email, enabled"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosedTestTester {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "normalized_email", nullable = false)
    private String normalizedEmail;

    @Column(nullable = false)
    private Boolean enabled;

    private String reason;

    @Column(nullable = false)
    private Long createdAt;

    private Long updatedAt;

    private Long disabledAt;

    @PrePersist
    @PreUpdate
    void normalizeEmail() {
        if (email != null) {
            email = email.trim();
            normalizedEmail = email.toLowerCase(Locale.ROOT);
        }
    }
}
