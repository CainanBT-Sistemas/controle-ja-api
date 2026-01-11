package com.cainanbt.softwares.controleja.utils;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface SecurityContextUtils {
    static Optional<Users> getUserLogged() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal != null && !principal.equals("anonymousUser")) {
            return Optional.of(((UserAuthenticateDTO) principal).getUsers());
        }
        return Optional.empty();
    }

}
