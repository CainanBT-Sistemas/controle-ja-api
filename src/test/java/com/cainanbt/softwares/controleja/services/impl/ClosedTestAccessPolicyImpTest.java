package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.exceptions.models.ForbiddenException;
import com.cainanbt.softwares.controleja.repositories.ClosedTestTesterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosedTestAccessPolicyImpTest {

    @Mock
    private ClosedTestTesterRepository testerRepository;

    private ClosedTestAccessPolicyImp enabledPolicy;

    @BeforeEach
    void setUp() {
        enabledPolicy = new ClosedTestAccessPolicyImp(true, testerRepository);
    }

    @Test
    void shouldPreserveNormalAccessWhenFeatureIsDisabled() {
        ClosedTestAccessPolicyImp policy = new ClosedTestAccessPolicyImp(false, testerRepository);

        assertFalse(policy.isEnabled());
        assertTrue(policy.isAccessAllowed("anyone@example.com"));
        assertDoesNotThrow(() -> policy.requireAccess("anyone@example.com"));
        verify(testerRepository, never()).existsByNormalizedEmailAndEnabledTrue("anyone@example.com");
    }

    @Test
    void shouldNormalizeCaseAndSpacesBeforeIndexedLookup() {
        when(testerRepository.existsByNormalizedEmailAndEnabledTrue("allowed@example.com"))
                .thenReturn(true);

        assertTrue(enabledPolicy.isAllowlisted(" allowed@example.COM "));
        assertTrue(enabledPolicy.isAccessAllowed("ALLOWED@example.com"));
        verify(testerRepository, times(2))
                .existsByNormalizedEmailAndEnabledTrue("allowed@example.com");
    }

    @Test
    void shouldDenyWhenTableHasNoActiveTester() {
        when(testerRepository.existsByNormalizedEmailAndEnabledTrue("anyone@example.com"))
                .thenReturn(false);

        assertFalse(enabledPolicy.isAccessAllowed("anyone@example.com"));
        assertThrows(ForbiddenException.class, () -> enabledPolicy.requireAccess("anyone@example.com"));
    }

    @Test
    void shouldRejectBlankEmailWithoutQueryingDatabase() {
        assertFalse(enabledPolicy.isAllowlisted(""));
        assertFalse(enabledPolicy.isAllowlisted(null));
        verify(testerRepository, never()).existsByNormalizedEmailAndEnabledTrue("");
    }

    @Test
    void shouldNotRewriteDotsOrPlusAliases() {
        when(testerRepository.existsByNormalizedEmailAndEnabledTrue("user.name+test@example.com"))
                .thenReturn(true);

        assertTrue(enabledPolicy.isAllowlisted("user.name+test@example.com"));
        verify(testerRepository).existsByNormalizedEmailAndEnabledTrue("user.name+test@example.com");
    }
}
