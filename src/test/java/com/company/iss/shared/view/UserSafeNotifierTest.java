package com.company.iss.shared.view;

import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSafeNotifierTest {

    @Test
    void keepsExpectedBusinessValidationActionable() {
        var error = UserSafeNotifier.classify(
                new BusinessRuleViolationException("Only active bookings can be cancelled.")
        );

        assertEquals("Only active bookings can be cancelled.", error.message());
        assertEquals(null, error.reference());
    }

    @Test
    void hidesAccessDeniedDetails() {
        var error = UserSafeNotifier.classify(new AccessDeniedException("Applicant 42 belongs to another branch."));

        assertEquals(UserSafeNotifier.ACCESS_DENIED_MESSAGE, error.message());
        assertFalse(error.message().contains("42"));
        assertEquals(null, error.reference());
    }

    @Test
    void unexpectedErrorsHaveCorrelationReferenceWithoutRawDetails() {
        var error = UserSafeNotifier.classify(
                new RuntimeException("SQL error: password=secret jdbc:mysql://internal-host/production")
        );

        assertNotNull(error.reference());
        assertTrue(error.message().contains(error.reference()));
        assertFalse(error.message().contains("password"));
        assertFalse(error.message().contains("internal-host"));
    }

    @Test
    void genericIllegalArgumentDetailsAreNeverTrusted() {
        var error = UserSafeNotifier.classify(
                new IllegalArgumentException("Invalid datasource password=super-secret")
        );

        assertNotNull(error.reference());
        assertFalse(error.message().contains("password"));
        assertFalse(error.message().contains("super-secret"));
    }

    @Test
    void genericIllegalStateDetailsAreNeverTrusted() {
        var error = UserSafeNotifier.classify(
                new IllegalStateException("SMTP apiKey=operator-secret")
        );

        assertNotNull(error.reference());
        assertFalse(error.message().contains("apiKey"));
        assertFalse(error.message().contains("operator-secret"));
    }
}
