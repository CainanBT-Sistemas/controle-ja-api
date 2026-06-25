package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosedTestAccessPolicyImpTest {

    @Test
    void shouldPreserveNormalAccessWhenFeatureIsDisabled() {
        ClosedTestAccessPolicyImp policy = new ClosedTestAccessPolicyImp(false, "");

        assertFalse(policy.isEnabled());
        assertTrue(policy.isAccessAllowed("anyone@example.com"));
        assertDoesNotThrow(() -> policy.requireAccess("anyone@example.com"));
    }

    @Test
    void shouldNormalizeCaseSpacesAndIgnoreEmptyEntries() {
        ClosedTestAccessPolicyImp policy = new ClosedTestAccessPolicyImp(
                true,
                "  Allowed@Example.com, ,second@example.com,  "
        );

        assertTrue(policy.isAllowlisted(" allowed@example.COM "));
        assertTrue(policy.isAccessAllowed("ALLOWED@example.com"));
        assertFalse(policy.isAllowlisted(""));
        assertFalse(policy.isAllowlisted(null));
        assertThrows(ForbiddenException.class, () -> policy.requireAccess("not-allowed@example.com"));
    }

    @Test
    void shouldDenyEveryoneWhenEnabledWithEmptyAllowlist() {
        ClosedTestAccessPolicyImp policy = new ClosedTestAccessPolicyImp(true, " ,  , ");

        assertFalse(policy.isAccessAllowed("anyone@example.com"));
        assertThrows(ForbiddenException.class, () -> policy.requireAccess("anyone@example.com"));
    }

    @Test
    void shouldNotRewriteDotsOrPlusAliases() {
        ClosedTestAccessPolicyImp policy = new ClosedTestAccessPolicyImp(true, "user.name+test@example.com");

        assertTrue(policy.isAllowlisted("user.name+test@example.com"));
        assertFalse(policy.isAllowlisted("username@example.com"));
        assertFalse(policy.isAllowlisted("user.name@example.com"));
    }
}
