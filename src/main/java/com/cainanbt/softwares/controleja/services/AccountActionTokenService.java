package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountActionTokenRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountActionTokenService {
    private static final String INVALID_CODE = "Código inválido ou expirado.";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AccountActionTokenRepository repository;
    private final AccountActionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Optional<String> issue(
            String normalizedEmail,
            String remoteAddress,
            AccountActionPurpose purpose,
            UUID userId
    ) {
        long now = DateUtils.getEpochNow();
        String emailHash = digest(normalizedEmail);
        String ipHash = digest(remoteAddress == null ? "unknown" : remoteAddress);

        long ipWindowStart = now - properties.getIpWindowMinutes() * 60_000L;
        if (repository.countByIpHashAndCreatedAtGreaterThanEqual(ipHash, ipWindowStart)
                >= properties.getIpMaxRequests()) {
            return Optional.empty();
        }

        Optional<AccountActionToken> latest = repository
                .findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(emailHash, purpose);
        if (latest.isPresent() && latest.get().getCreatedAt()
                > now - properties.getCooldownSeconds() * 1_000L) {
            return Optional.empty();
        }

        repository.invalidateActive(emailHash, purpose, now);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        repository.save(AccountActionToken.builder()
                .id(ID.generate())
                .userId(userId)
                .purpose(purpose)
                .emailHash(emailHash)
                .ipHash(ipHash)
                .codeDigest(codeDigest(emailHash, purpose, code))
                .expiresAt(now + properties.getExpiryMinutes() * 60_000L)
                .attempts(0)
                .maxAttempts(properties.getMaxAttempts())
                .createdAt(now)
                .build());
        return Optional.of(code);
    }

    public AccountActionToken verifyForUpdate(
            String normalizedEmail,
            AccountActionPurpose purpose,
            String code
    ) {
        String emailHash = digest(normalizedEmail);
        AccountActionToken token = repository
                .findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(emailHash, purpose)
                .orElseThrow(this::invalidCode);
        long now = DateUtils.getEpochNow();
        if (token.getConsumedAt() != null || token.getInvalidatedAt() != null
                || token.getExpiresAt() < now || token.getAttempts() >= token.getMaxAttempts()) {
            throw invalidCode();
        }

        byte[] expected = token.getCodeDigest().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = codeDigest(emailHash, purpose, code).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= token.getMaxAttempts()) {
                token.setInvalidatedAt(now);
            }
            repository.save(token);
            throw invalidCode();
        }
        return token;
    }

    public void consume(AccountActionToken token) {
        token.setConsumedAt(DateUtils.getEpochNow());
        repository.save(token);
    }

    public void invalidate(AccountActionToken token) {
        token.setInvalidatedAt(DateUtils.getEpochNow());
        repository.save(token);
    }

    private String codeDigest(String emailHash, AccountActionPurpose purpose, String code) {
        return digest(emailHash + ":" + purpose.name() + ":" + code);
    }

    private String digest(String value) {
        String pepper = properties.getPepper();
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("ACCOUNT_ACTION_TOKEN_PEPPER is required");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect account action token", exception);
        }
    }

    private BadRequestException invalidCode() {
        return new BadRequestException("Código inválido", INVALID_CODE);
    }
}
