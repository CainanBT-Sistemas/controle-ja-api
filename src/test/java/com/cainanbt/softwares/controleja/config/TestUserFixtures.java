package com.cainanbt.softwares.controleja.config;

import com.cainanbt.softwares.controleja.entities.ClosedTestTester;
import com.cainanbt.softwares.controleja.utils.DateUtils;
import com.cainanbt.softwares.controleja.utils.ID;

import java.util.Locale;

public final class TestUserFixtures {

    public static final String TESTER_EMAIL = "tester@controleja.local";

    private TestUserFixtures() {
    }

    public static ClosedTestTester activeTester(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return ClosedTestTester.builder()
                .id(ID.generate())
                .email(email.trim())
                .normalizedEmail(normalizedEmail)
                .enabled(true)
                .reason("Automated test")
                .createdAt(DateUtils.getEpochNow())
                .build();
    }
}
