package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.dtos.ReactivationConfirmDTO;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountReactivationService {
    private final AccountActionTokenService tokenService;
    private final UsersRepository usersRepository;
    private final AccountDeletionService accountDeletionService;
    private final AccountActionProperties properties;

    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirm(ReactivationConfirmDTO request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AccountActionToken token = tokenService.verifyForUpdate(
                email,
                AccountActionPurpose.ACCOUNT_REACTIVATION,
                request.getCode()
        );
        Users user = usersRepository.findAnyByEmailForUpdate(email)
                .orElseThrow(this::invalidCode);
        long retentionStart = System.currentTimeMillis() - properties.getReactivationDays() * 86_400_000L;
        if (user.getDeletedAt() == null || user.getDeletedAt() < retentionStart
                || !user.getId().equals(token.getUserId())) {
            tokenService.invalidate(token);
            throw invalidCode();
        }

        accountDeletionService.reactivate(user);
        usersRepository.save(user);
        tokenService.consume(token);
    }

    private BadRequestException invalidCode() {
        return new BadRequestException("Código inválido", "Código inválido ou expirado.");
    }
}
