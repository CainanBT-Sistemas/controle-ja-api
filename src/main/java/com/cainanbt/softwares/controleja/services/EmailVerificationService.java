package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.EmailVerificationConfirmDTO;
import com.cainanbt.softwares.controleja.dtos.EmailVerificationRequestDTO;
import com.cainanbt.softwares.controleja.dtos.responses.AccountRecoveryRequestResponseDTO;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final String REQUEST_MESSAGE =
            "Solicitação recebida. Se houver uma conta pendente, enviaremos um e-mail com o código de confirmação.";

    private final UsersRepository usersRepository;
    private final AccountActionTokenService tokenService;
    private final AccountActionEmailSender emailSender;
    private final AccountActionProperties properties;

    public AccountRecoveryRequestResponseDTO request(EmailVerificationRequestDTO request, String remoteAddress) {
        String email = normalize(request.getEmail());
        Users user = usersRepository.findAnyByEmail(email)
                .filter(this::isEligible)
                .orElse(null);
        issueAndSend(email, remoteAddress, user);
        return response();
    }

    public void requestAfterRegistration(Users user, String remoteAddress) {
        if (!isEligible(user)) {
            return;
        }
        issueAndSend(user.getEmail(), remoteAddress, user);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirm(EmailVerificationConfirmDTO request) {
        String email = normalize(request.getEmail());
        AccountActionToken token = tokenService.verifyForUpdate(
                email,
                AccountActionPurpose.EMAIL_VERIFICATION,
                request.getCode()
        );
        Users user = usersRepository.findAnyByEmailForUpdate(email)
                .orElseThrow(this::invalidCode);
        if (!isEligible(user) || !user.getId().equals(token.getUserId())) {
            tokenService.invalidate(token);
            throw invalidCode();
        }

        user.setEmailVerified(true);
        user.setUpdatedAt(DateUtils.getEpochNow());
        usersRepository.save(user);
        tokenService.consume(token);
    }

    private void issueAndSend(String email, String remoteAddress, Users user) {
        if (user == null) {
            return;
        }
        Optional<String> code = tokenService.issue(
                email,
                remoteAddress,
                AccountActionPurpose.EMAIL_VERIFICATION,
                user.getId()
        );
        if (code.isEmpty()) {
            return;
        }
        try {
            emailSender.sendCode(
                    email,
                    code.get(),
                    AccountActionPurpose.EMAIL_VERIFICATION,
                    properties.getExpiryMinutes()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "account_action_email_delivery_failed purpose={} exception={}",
                    AccountActionPurpose.EMAIL_VERIFICATION,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private boolean isEligible(Users user) {
        return user != null
                && user.getDeletedAt() == null
                && Boolean.TRUE.equals(user.getEnabled())
                && Boolean.TRUE.equals(user.getLocalPasswordEnabled())
                && !Boolean.TRUE.equals(user.getEmailVerified());
    }

    private AccountRecoveryRequestResponseDTO response() {
        String sender = properties.getSender();
        return new AccountRecoveryRequestResponseDTO(
                REQUEST_MESSAGE,
                sender == null || sender.isBlank() ? null : sender.trim()
        );
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private BadRequestException invalidCode() {
        return new BadRequestException("Código inválido", "Código inválido ou expirado.");
    }
}
