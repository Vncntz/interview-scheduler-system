package com.company.iss.hiring.config;

import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private static final String CONFLICTING_ZONE_MESSAGE =
            "OFFER_RESPONSE_TIMESTAMP_ZONE (UTC) does not have equivalent time-zone behavior from "
                    + "2026-01-01T00:00:00Z onward as the effective "
                    + "iss.hiring.offer-deadline.timestamp-zone (Asia/Manila). Remove the higher-precedence "
                    + "override or configure it with future behavior equivalent to the historical zone before startup.";

    @Mock
    HiringDecisionRepository hiringDecisionRepository;

    @Mock
    ObjectProvider<OfferDeadlineProperties> offerDeadlinePropertiesProvider;

    MockEnvironment environment;
    OfferDeadlineProperties offerDeadlineProperties;
    OfferTimestampZoneStartupGuard guard;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        offerDeadlineProperties = new OfferDeadlineProperties();
        lenient().when(offerDeadlinePropertiesProvider.getObject()).thenReturn(offerDeadlineProperties);
        guard = new OfferTimestampZoneStartupGuard(
                hiringDecisionRepository,
                environment,
                offerDeadlinePropertiesProvider
        );
    }

    @Test
    void absentVariableAllowsStartupWhenNoHiringDecisionsExist() {
        when(hiringDecisionRepository.count()).thenReturn(0L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

        verify(hiringDecisionRepository).count();
    }

    @Test
    void absentVariableRejectsStartupWhenHiringDecisionsExist() {
        when(hiringDecisionRepository.count()).thenReturn(1L);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                guard::afterSingletonsInstantiated
        );

        assertEquals(MISSING_ZONE_MESSAGE, failure.getMessage());
        verify(hiringDecisionRepository).count();
    }

    @Test
    void explicitManilaVariableAllowsExistingHiringDecisionsWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "Asia/Manila");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitUtcVariableAllowsExistingHiringDecisionsWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "UTC");
        offerDeadlineProperties.setTimestampZone(ZoneId.of("UTC"));
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitUtcVariableAllowsEquivalentEtcUtcEffectiveZoneWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "UTC");
        offerDeadlineProperties.setTimestampZone(ZoneId.of("Etc/UTC"));
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitManilaVariableAllowsFixedEightHourEffectiveZoneWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "Asia/Manila");
        offerDeadlineProperties.setTimestampZone(ZoneId.of("+08:00"));
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void compatibilityIgnoresTransitionsBeforeTheSupportedRecordBoundary() {
        ZoneOffset plusEight = ZoneOffset.ofHours(8);
        ZoneOffset plusNine = ZoneOffset.ofHours(9);
        ZoneOffsetTransition historicalTransition = ZoneOffsetTransition.of(
                LocalDateTime.of(2025, 1, 1, 0, 0),
                plusNine,
                plusEight
        );
        ZoneRules historicallyChangedRules = ZoneRules.of(
                plusEight,
                plusNine,
                List.of(),
                List.of(historicalTransition),
                List.of()
        );

        assertTrue(OfferTimestampZoneStartupGuard.haveEquivalentBehaviorInSupportedHiringRecordEra(
                historicallyChangedRules,
                ZoneRules.of(plusEight)
        ));
    }

    @Test
    void compatibilityRejectsDifferentTransitionsAfterTheSupportedRecordBoundary() {
        ZoneOffset plusEight = ZoneOffset.ofHours(8);
        ZoneOffset plusNine = ZoneOffset.ofHours(9);
        ZoneOffsetTransition futureTransition = ZoneOffsetTransition.of(
                LocalDateTime.of(2027, 1, 1, 0, 0),
                plusEight,
                plusNine
        );
        ZoneRules changingRules = ZoneRules.of(
                plusEight,
                plusEight,
                List.of(),
                List.of(futureTransition),
                List.of()
        );

        assertFalse(OfferTimestampZoneStartupGuard.haveEquivalentBehaviorInSupportedHiringRecordEra(
                changingRules,
                ZoneRules.of(plusEight)
        ));
    }

    @Test
    void explicitVariableRejectsAConflictingEffectiveZoneWithoutCounting() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "UTC");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                guard::afterSingletonsInstantiated
        );

        assertEquals(CONFLICTING_ZONE_MESSAGE, failure.getMessage());
        verify(hiringDecisionRepository, never()).count();
    }

    @Test
    void explicitBlankVariableIsNotTreatedAsMissingByGuard() {
        environment.setProperty("OFFER_RESPONSE_TIMESTAMP_ZONE", "   ");
        lenient().when(hiringDecisionRepository.count()).thenReturn(1L);

        assertDoesNotThrow(guard::afterSingletonsInstantiated);

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
