package com.cainanbt.softwares.controleja.configs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountActionPropertiesTest {

    @Test
    void rejectsMissingPepperDuringStartupValidation() {
        AccountActionProperties properties = new AccountActionProperties();

        assertThrows(IllegalStateException.class, properties::validate);

        properties.setPepper("   ");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void acceptsConfiguredPepperDuringStartupValidation() {
        AccountActionProperties properties = new AccountActionProperties();
        properties.setPepper("test-only-account-action-pepper");

        assertDoesNotThrow(properties::validate);
    }
}
