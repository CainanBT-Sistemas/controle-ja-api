package com.cainanbt.softwares.controleja.utils;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.exceptions.models.BadRequestException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public interface SecurityContextUtils {
    static Optional<Users> getUserLogged() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserAuthenticateDTO userAuth) {
                    return Optional.of(userAuth.getUsers());
                }
            }
        } catch (Exception e) {
        }
        return Optional.empty();
    }

    static Users getCurrentUser() {
        return getUserLogged()
                .orElseThrow(() -> new BadRequestException("Acesso Negado", "Usuário não autenticado ou sessão expirada."));
    }
}
