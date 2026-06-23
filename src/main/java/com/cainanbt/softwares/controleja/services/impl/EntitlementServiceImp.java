package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementPermissionsDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntitlementServiceImp implements EntitlementService {

    private final Set<String> testerEmails;

    public EntitlementServiceImp(@Value("${app.config.entitlements.tester-emails:}") String testerEmails) {
        this.testerEmails = Arrays.stream(testerEmails.split(","))
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public UserEntitlementsDTO buildForUser(Users user) {
        boolean tester = user != null
                && user.getEmail() != null
                && testerEmails.contains(user.getEmail().trim().toLowerCase(Locale.ROOT));

        if (tester) {
            return new UserEntitlementsDTO(
                    "TESTER",
                    true,
                    true,
                    false,
                    new UserEntitlementPermissionsDTO(true, true, true)
            );
        }

        return new UserEntitlementsDTO(
                "FREE",
                false,
                false,
                true,
                new UserEntitlementPermissionsDTO(false, false, false)
        );
    }
}
