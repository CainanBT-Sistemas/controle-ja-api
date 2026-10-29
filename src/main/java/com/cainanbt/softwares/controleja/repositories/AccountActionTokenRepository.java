package com.cainanbt.softwares.controleja.repositories;

import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountActionToken> findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(
            String emailHash,
            AccountActionPurpose purpose
    );

    long countByIpHashAndCreatedAtGreaterThanEqual(String ipHash, Long createdAt);

    @Modifying
    @Query("UPDATE AccountActionToken t SET t.invalidatedAt = :now WHERE t.emailHash = :emailHash " +
            "AND t.purpose = :purpose AND t.consumedAt IS NULL AND t.invalidatedAt IS NULL")
    int invalidateActive(
            @Param("emailHash") String emailHash,
            @Param("purpose") AccountActionPurpose purpose,
            @Param("now") Long now
    );
}
