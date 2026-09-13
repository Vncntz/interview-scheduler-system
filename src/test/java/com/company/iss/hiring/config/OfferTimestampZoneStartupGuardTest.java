package com.company.iss.hiring.config;

import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferTimestampZoneStartupGuardTest {

    private static final String MISSING_ZONE_MESSAGE =
            "Existing hiring decisions use zone-less timestamps. Set OFFER_RESPONSE_TIMESTAMP_ZONE explicitly "
                    + "to the historical zone before startup; the Asia/Manila default is safe only when no hiring "
                    + "decisions exist. If the historical zone has fall-back overlaps, complete an approved "
                    + "timestamp backfill/storage migration before starting.";
    private static final String BLANK_ZONE_MESSAGE = "OFFER_RESPONSE_TIMESTAMP_ZONE must not be blank.";

    @Mock
    HiringDecisionRepository hiringDecisionRepository;

    MockEnvironment environment;
    OfferTimestampZoneStartupGuard guard;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        guard = new OfferTimestampZoneStartupGuard(hiringDecisionRepository, environment);
    }

    @Test
    void absentVariableAllowsStartupWhenNoHiringDecisionsExist() {
        when(hiringDecisionRepository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> guard.run(null));

        verify(hiringDecisionRepository).count();
    }

    @Test
    void absentVariableRejectsStartupWhenHiringDecisionsExist() {
        when(hiringDecisionRepository.count()).thenReturn(1L);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> guard.run(null));

        assertEquals(MISSING_ZONE_MESSAGE, failure.getMessage());
        verify(hiringDecisionRepository).count();
    }

    @Test
    void explicitManilaVariableAllowsExistingHiringDecisionsWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "Asia/Manila");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(() -> guard.run(null));

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitUtcVariableAllowsExistingHiringDecisionsWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "UTC");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(() -> guard.run(null));

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitBlankVariableIsNotTreatedAsMissingByGuard() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "   ");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(() -> guard.run(null));

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitBlankVariableFailsInitializationClearly() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "   ");

        IllegalStateException failure = assertThrows(IllegalStateException.class, guard::afterPropertiesSet);

        assertEquals(BLANK_ZONE_MESSAGE, failure.getMessage());
        verify(hiringDecisionRepository, never()).count();
    }
}
