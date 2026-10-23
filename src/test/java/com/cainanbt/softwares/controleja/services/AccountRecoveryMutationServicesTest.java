package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.PasswordResetConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.ReactivationConfirmDTO;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryMutationServicesTest {
    @Mock
    private AccountActionTokenService tokenService;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordRecoveryService passwordRecoveryService;
    private AccountReactivationService reactivationService;

    @BeforeEach
    void setup() {
        AccountActionProperties properties = new AccountActionProperties();
        properties.setReactivationDays(60);
        passwordRecoveryService = new PasswordRecoveryService(tokenService, usersRepository, passwordEncoder);
        reactivationService = new AccountReactivationService(
                tokenService,
                usersRepository,
                new AccountDeletionService(),
                properties
        );
    }

    @Test
    void resetsPasswordInvalidatesRefreshAndConsumesToken() {
        UUID userId = UUID.randomUUID();
        AccountActionToken token = AccountActionToken.builder().userId(userId).build();
        Users user = activeUser(userId);
        when(tokenService.verifyForUpdate(any(), any(), any())).thenReturn(token);
        when(usersRepository.findAnyByEmailForUpdate("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("654321")).thenReturn("encoded-new-password");

        passwordRecoveryService.confirm(passwordResetRequest());

        assertEquals("encoded-new-password", user.getPassword());
        assertNull(user.getRefreshToken());
        assertEquals(0L, user.getRefreshTokenExpiry());
        verify(usersRepository).save(user);
        verify(tokenService).consume(token);
    }

    @Test
    void refusesToCreatePasswordForGoogleOnlyAccount() {
        UUID userId = UUID.randomUUID();
        AccountActionToken token = AccountActionToken.builder().userId(userId).build();
        Users user = activeUser(userId);
        user.setLocalPasswordEnabled(false);
        when(tokenService.verifyForUpdate(any(), any(), any())).thenReturn(token);
        when(usersRepository.findAnyByEmailForUpdate("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> passwordRecoveryService.confirm(passwordResetRequest()));

        verify(tokenService).invalidate(token);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void reactivatesSameUserWithoutCreatingSession() {
        UUID userId = UUID.randomUUID();
        AccountActionToken token = AccountActionToken.builder().userId(userId).build();
        Users user = activeUser(userId);
        user.setDeletedAt(System.currentTimeMillis() - 86_400_000L);
        user.setEnabled(false);
        user.setAccountNonLocked(false);
        when(tokenService.verifyForUpdate(any(), any(), any())).thenReturn(token);
        when(usersRepository.findAnyByEmailForUpdate("user@test.com")).thenReturn(Optional.of(user));

        ReactivationConfirmDTO request = new ReactivationConfirmDTO();
        request.setEmail("user@test.com");
        request.setCode("012345");
        reactivationService.confirm(request);

        assertNull(user.getDeletedAt());
        assertTrue(user.getEnabled());
        assertTrue(user.getAccountNonLocked());
        assertNull(user.getRefreshToken());
        verify(usersRepository).save(user);
        verify(tokenService).consume(token);
    }

    @Test
    void doesNotConsumeTokenWhenPasswordPersistenceFails() {
        UUID userId = UUID.randomUUID();
        AccountActionToken token = AccountActionToken.builder().userId(userId).build();
        Users user = activeUser(userId);
        when(tokenService.verifyForUpdate(any(), any(), any())).thenReturn(token);
        when(usersRepository.findAnyByEmailForUpdate("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("654321")).thenReturn("encoded-new-password");
        doThrow(new IllegalStateException("persistence failed")).when(usersRepository).save(user);

        assertThrows(IllegalStateException.class, () -> passwordRecoveryService.confirm(passwordResetRequest()));

        verify(tokenService, never()).consume(token);
    }

    private PasswordResetConfirmDTO passwordResetRequest() {
        PasswordResetConfirmDTO request = new PasswordResetConfirmDTO();
        request.setEmail("user@test.com");
        request.setCode("012345");
        request.setNewPassword("654321");
        return request;
    }

    private Users activeUser(UUID id) {
        return Users.builder()
                .id(id)
                .email("user@test.com")
                .password("old-password")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .localPasswordEnabled(true)
                .refreshToken("old-refresh")
                .refreshTokenExpiry(123L)
                .build();
    }
}
