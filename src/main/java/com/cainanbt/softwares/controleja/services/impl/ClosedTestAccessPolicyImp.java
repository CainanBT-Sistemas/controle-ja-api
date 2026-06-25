package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.repositories.ClosedTestTesterRepository;
import com.cainanbt.softwares.controleja.services.ClosedTestAccessPolicy;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ClosedTestAccessPolicyImp implements ClosedTestAccessPolicy {

    private final boolean enabled;
    private final ClosedTestTesterRepository testerRepository;

    public ClosedTestAccessPolicyImp(
            @Value("${app.config.closed-test.enabled:false}") boolean enabled,
            ClosedTestTesterRepository testerRepository
    ) {
        this.enabled = enabled;
        this.testerRepository = testerRepository;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAllowlisted(String email) {
        String normalizedEmail = normalize(email);
        return !normalizedEmail.isBlank()
                && testerRepository.existsByNormalizedEmailAndEnabledTrue(normalizedEmail);
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
