package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.AccountActionTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccountActionTokenServiceTest {
    @Mock
    private AccountActionTokenRepository repository;

    private final AtomicReference<AccountActionToken> stored = new AtomicReference<>();
    private AccountActionProperties properties;
    private AccountActionTokenService service;

    @BeforeEach
    void setup() {
        properties = new AccountActionProperties();
        properties.setPepper("unit-test-pepper");
        properties.setCooldownSeconds(0);
        properties.setIpMaxRequests(10);
        properties.setMaxAttempts(5);
        properties.setExpiryMinutes(15);
        service = new AccountActionTokenService(repository, properties);

        when(repository.countByIpHashAndCreatedAtGreaterThanEqual(anyString(), anyLong())).thenReturn(0L);
        lenient().when(repository.invalidateActive(anyString(), any(), anyLong())).thenReturn(0);
        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            AccountActionToken token = invocation.getArgument(0);
            stored.set(token);
            return token;
        });
    }

    @Test
    void generatesSixDigitCodeAndConsumesItOnlyOnce() {
        when(repository.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String code = service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        ).orElseThrow();

        assertTrue(code.matches("\\d{6}"));
        AccountActionToken token = service.verifyForUpdate(
                "user@test.com",
                AccountActionPurpose.PASSWORD_RESET,
                code
        );
        service.consume(token);
        assertThrows(BadRequestException.class, () -> service.verifyForUpdate(
                "user@test.com",
                AccountActionPurpose.PASSWORD_RESET,
                code
        ));
    }

    @Test
    void invalidatesAfterFiveWrongAttempts() {
        when(repository.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        String code = service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        ).orElseThrow();
        String wrongCode = code.equals("999999") ? "888888" : "999999";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(BadRequestException.class, () -> service.verifyForUpdate(
                    "user@test.com",
                    AccountActionPurpose.PASSWORD_RESET,
                    wrongCode
            ));
        }

        assertEquals(5, stored.get().getAttempts());
        assertTrue(stored.get().getInvalidatedAt() != null);
        assertThrows(BadRequestException.class, () -> service.verifyForUpdate(
                "user@test.com",
                AccountActionPurpose.PASSWORD_RESET,
                code
        ));
    }

    @Test
    void rejectsExpiredCode() {
        when(repository.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        String code = service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.ACCOUNT_REACTIVATION,
                null
        ).orElseThrow();
        stored.get().setExpiresAt(System.currentTimeMillis() - 1);

        assertThrows(BadRequestException.class, () -> service.verifyForUpdate(
                "user@test.com",
                AccountActionPurpose.ACCOUNT_REACTIVATION,
                code
        ));
    }

    @Test
    void storesOnlyDigestAndHonorsEmailCooldown() {
        properties.setCooldownSeconds(60);
        when(repository.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(anyString(), any()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        String code = service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        ).orElseThrow();

        assertFalse(stored.get().getCodeDigest().contains(code));
        assertTrue(service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        ).isEmpty());
    }

    @Test
    void honorsIpRateLimit() {
        when(repository.countByIpHashAndCreatedAtGreaterThanEqual(anyString(), anyLong())).thenReturn(10L);

        assertTrue(service.issue(
                "user@test.com",
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        ).isEmpty());
    }
}
