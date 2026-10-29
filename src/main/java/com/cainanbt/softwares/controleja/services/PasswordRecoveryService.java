package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.PasswordResetConfirmDTO;
import com.cainanbt.softwares.controleja.entities.AccountActionToken;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import com.cainanbt.softwares.controleja.repositories.UsersRepository;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private final AccountActionTokenService tokenService;
    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirm(PasswordResetConfirmDTO request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AccountActionToken token = tokenService.verifyForUpdate(
                email,
                AccountActionPurpose.PASSWORD_RESET,
                request.getCode()
        );
        Users user = usersRepository.findAnyByEmailForUpdate(email)
                .orElseThrow(this::invalidCode);
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getEnabled())
                || !Boolean.TRUE.equals(user.getLocalPasswordEnabled())
                || !user.getId().equals(token.getUserId())) {
            tokenService.invalidate(token);
            throw invalidCode();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(0L);
        user.setSessionVersion(user.getSessionVersion() + 1L);
        user.setUpdatedAt(DateUtils.getEpochNow());
        usersRepository.save(user);
        tokenService.consume(token);
    }

    private BadRequestException invalidCode() {
        return new BadRequestException("Código inválido", "Código inválido ou expirado.");
    }
}
