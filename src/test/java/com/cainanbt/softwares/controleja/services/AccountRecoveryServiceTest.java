package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.AccountRecoveryRequestDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountRecoveryRequestResponseDTO;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private AccountActionTokenService tokenService;
    @Mock
    private AccountActionEmailSender emailSender;

    private AccountRecoveryService service;

    @BeforeEach
    void setup() {
        AccountActionProperties properties = new AccountActionProperties();
        properties.setSender("no-reply@controleja.test");
        service = new AccountRecoveryService(usersRepository, tokenService, emailSender, properties);
    }

    @Test
    void returnsSamePublicResponseForEligibleAndUnknownAccounts() {
        String knownEmail = "known@test.com";
        String unknownEmail = "unknown@test.com";
        UUID userId = UUID.randomUUID();
        Users activeUser = Users.builder()
                .id(userId)
                .enabled(true)
                .localPasswordEnabled(true)
                .build();
        when(usersRepository.findAnyByEmail(knownEmail)).thenReturn(Optional.of(activeUser));
        when(usersRepository.findAnyByEmail(unknownEmail)).thenReturn(Optional.empty());
        when(tokenService.issue(
                knownEmail,
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                userId
        )).thenReturn(Optional.of("012345"));
        when(tokenService.issue(
                unknownEmail,
                "127.0.0.1",
                AccountActionPurpose.PASSWORD_RESET,
                null
        )).thenReturn(Optional.empty());

        AccountRecoveryRequestResponseDTO knownResponse = service.request(
                request(knownEmail, AccountActionPurpose.PASSWORD_RESET),
                "127.0.0.1"
        );
        AccountRecoveryRequestResponseDTO unknownResponse = service.request(
                request(unknownEmail, AccountActionPurpose.PASSWORD_RESET),
                "127.0.0.1"
        );

        assertEquals(knownResponse.getMessage(), unknownResponse.getMessage());
        assertEquals("no-reply@controleja.test", knownResponse.getSenderEmail());
        assertEquals(knownResponse.getSenderEmail(), unknownResponse.getSenderEmail());
    }

    private AccountRecoveryRequestDTO request(String email, AccountActionPurpose purpose) {
        AccountRecoveryRequestDTO request = new AccountRecoveryRequestDTO();
        request.setEmail(email);
        request.setPurpose(purpose);
        return request;
    }
}
