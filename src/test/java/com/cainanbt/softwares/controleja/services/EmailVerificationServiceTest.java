package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.EmailVerificationConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.EmailVerificationRequestDTO;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private AccountActionTokenService tokenService;
    @Mock
    private AccountActionEmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        AccountActionProperties properties = new AccountActionProperties();
        properties.setSender("support@controleja.test");
        properties.setExpiryMinutes(15);
        service = new EmailVerificationService(usersRepository, tokenService, emailSender, properties);
    }

    @Test
    void requestUsesGenericResponseAndSendsOnlyToEligibleLocalAccount() {
        Users user = pendingUser();
        EmailVerificationRequestDTO request = request(user.getEmail());
        when(usersRepository.findAnyByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenService.issue(
                user.getEmail(),
                "127.0.0.1",
                AccountActionPurpose.EMAIL_VERIFICATION,
                user.getId()
        )).thenReturn(Optional.of("123456"));

        var response = service.request(request, "127.0.0.1");

        assertEquals("support@controleja.test", response.getSenderEmail());
        verify(emailSender).sendCode(
                user.getEmail(),
                "123456",
                AccountActionPurpose.EMAIL_VERIFICATION,
                15
        );
    }

    @Test
    void requestDoesNotSendToVerifiedAccount() {
        Users user = pendingUser();
        user.setEmailVerified(true);
        when(usersRepository.findAnyByEmail(user.getEmail())).thenReturn(Optional.of(user));
        service.request(request(user.getEmail()), "127.0.0.1");

        verify(tokenService, never()).issue(any(), any(), any(), any());
        verify(emailSender, never()).sendCode(any(), any(), any(), anyInt());
    }

    @Test
    void requestDoesNotCreateTokenForUnknownAccount() {
        String email = "unknown@test.com";
        when(usersRepository.findAnyByEmail(email)).thenReturn(Optional.empty());

        var response = service.request(request(email), "127.0.0.1");

        assertEquals("support@controleja.test", response.getSenderEmail());
        verify(tokenService, never()).issue(any(), any(), any(), any());
        verify(emailSender, never()).sendCode(any(), any(), any(), anyInt());
    }

    @Test
    void confirmVerifiesUserAndConsumesBoundToken() {
        Users user = pendingUser();
        AccountActionToken token = AccountActionToken.builder().userId(user.getId()).build();
        EmailVerificationConfirmDTO request = new EmailVerificationConfirmDTO();
        request.setEmail(user.getEmail());
        request.setCode("123456");
        when(tokenService.verifyForUpdate(
                user.getEmail(),
                AccountActionPurpose.EMAIL_VERIFICATION,
                "123456"
        )).thenReturn(token);
        when(usersRepository.findAnyByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        service.confirm(request);

        assertTrue(user.getEmailVerified());
        verify(usersRepository).save(user);
        verify(tokenService).consume(token);
    }

    private EmailVerificationRequestDTO request(String email) {
        EmailVerificationRequestDTO request = new EmailVerificationRequestDTO();
        request.setEmail(email);
        return request;
    }

    private Users pendingUser() {
        return Users.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .enabled(true)
                .localPasswordEnabled(true)
                .emailVerified(false)
                .build();
    }
}
