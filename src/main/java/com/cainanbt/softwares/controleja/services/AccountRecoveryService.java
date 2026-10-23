package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.AccountRecoveryRequestDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
    private final UsersRepository usersRepository;
    private final AccountActionTokenService tokenService;
    private final AccountActionEmailSender emailSender;
    private final AccountActionProperties properties;

    public void request(AccountRecoveryRequestDTO request, String remoteAddress) {
        String email = normalize(request.getEmail());
        Optional<Users> user = usersRepository.findAnyByEmail(email);
        Users eligibleUser = isEligible(user, request.getPurpose()) ? user.orElseThrow() : null;
        Optional<String> code = tokenService.issue(
                email,
                remoteAddress,
                request.getPurpose(),
                eligibleUser == null ? null : eligibleUser.getId()
        );
        if (code.isEmpty() || eligibleUser == null) {
            return;
        }

        try {
            if (request.getPurpose() == AccountActionPurpose.PASSWORD_RESET
                    && !Boolean.TRUE.equals(eligibleUser.getLocalPasswordEnabled())) {
                emailSender.sendGoogleAccessGuidance(email);
            } else {
                emailSender.sendCode(email, code.get(), request.getPurpose(), properties.getExpiryMinutes());
            }
        } catch (RuntimeException exception) {
            log.error(
                    "account_action_email_delivery_failed purpose={} exception={}",
                    request.getPurpose(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private boolean isEligible(Optional<Users> user, AccountActionPurpose purpose) {
        if (user.isEmpty()) {
            return false;
        }
        Users candidate = user.get();
        if (purpose == AccountActionPurpose.PASSWORD_RESET) {
            return candidate.getDeletedAt() == null && Boolean.TRUE.equals(candidate.getEnabled());
        }
        if (candidate.getDeletedAt() == null) {
            return false;
        }
        long retentionStart = System.currentTimeMillis() - properties.getReactivationDays() * 86_400_000L;
        return candidate.getDeletedAt() >= retentionStart;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
