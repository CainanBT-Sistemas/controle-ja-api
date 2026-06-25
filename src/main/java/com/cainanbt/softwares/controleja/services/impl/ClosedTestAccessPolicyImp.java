package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.services.ClosedTestAccessPolicy;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClosedTestAccessPolicyImp implements ClosedTestAccessPolicy {

    private final boolean enabled;
    private final Set<String> allowedEmails;

    public ClosedTestAccessPolicyImp(
            @Value("${app.config.closed-test.enabled:false}") boolean enabled,
            @Value("${app.config.entitlements.tester-emails:}") String testerEmails
    ) {
        this.enabled = enabled;
        this.allowedEmails = Arrays.stream(testerEmails.split(","))
                .map(ClosedTestAccessPolicyImp::normalize)
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAllowlisted(String email) {
        return allowedEmails.contains(normalize(email));
    }

    @Override
    public boolean isAccessAllowed(String email) {
        return !enabled || isAllowlisted(email);
    }

    @Override
    public void requireAccess(String email) {
        if (!isAccessAllowed(email)) {
            throw new ForbiddenException(
                    ConstsMessages.CLOSED_TEST_TITLE,
                    ConstsMessages.CLOSED_TEST_ACCESS_DENIED
            );
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
