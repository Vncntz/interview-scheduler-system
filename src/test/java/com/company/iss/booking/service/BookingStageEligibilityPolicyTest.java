package com.company.iss.booking.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingStageEligibilityPolicyTest {

    private final BookingStageEligibilityPolicy policy = new BookingStageEligibilityPolicy();

    @ParameterizedTest
    @MethodSource("eligibleStatuses")
    void mapsRecruitmentStatusToRequiredStage(ApplicantStatus status, InterviewStage expected) {
        assertEquals(expected, policy.requiredStage(status, null));
    }

    @ParameterizedTest
    @EnumSource(value = ApplicantStatus.class, names = {
            "INTERVIEWED", "ON_HOLD", "PASSED", "FAILED", "OFFERED", "HIRED",
            "OFFER_DECLINED", "WITHDRAWN"
    })
    void rejectsUnsafeApplicantStates(ApplicantStatus status) {
        assertThrows(BusinessRuleViolationException.class, () -> policy.requiredStage(status, null));
    }

    @ParameterizedTest
    @EnumSource(InterviewStage.class)
    void cancelledReplacementPreservesPreviousStage(InterviewStage stage) {
        Booking previous = Booking.forInterviewStage(stage);
        previous.setStatus(BookingStatus.CANCELLED);

        assertEquals(stage, policy.requiredStage(ApplicantStatus.SCHEDULED, previous));
    }

    @ParameterizedTest
    @EnumSource(InterviewStage.class)
    void noShowReplacementPreservesPreviousStage(InterviewStage stage) {
        Booking previous = Booking.forInterviewStage(stage);
        previous.setStatus(BookingStatus.NO_SHOW);

        assertEquals(stage, policy.requiredStage(ApplicantStatus.SCHEDULED, previous));
    }

    @ParameterizedTest
    @EnumSource(InterviewStage.class)
    void projectionFriendlyOverloadPreservesReplacementStage(InterviewStage stage) {
        assertEquals(
                stage,
                policy.requiredStage(ApplicantStatus.SCHEDULED, BookingStatus.CANCELLED, stage)
        );
        assertEquals(
                stage,
                policy.requiredStage(ApplicantStatus.SCHEDULED, BookingStatus.NO_SHOW, stage)
        );
    }

    @Test
    void scheduledStateWithoutCancelledOrNoShowBookingIsRejected() {
        Booking active = Booking.forInterviewStage(InterviewStage.FINAL);
        active.setStatus(BookingStatus.CONFIRMED);

        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.requiredStage(ApplicantStatus.SCHEDULED, active)
        );
    }

    @Test
    void requestedStageMustMatchRequiredStage() {
        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validateRequestedStage(
                        ApplicantStatus.FOR_FINAL_INTERVIEW,
                        null,
                        InterviewStage.CLIENT
                )
        );
    }

    private static Stream<Arguments> eligibleStatuses() {
        return Stream.of(
                Arguments.of(ApplicantStatus.NEW, InterviewStage.INITIAL),
                Arguments.of(ApplicantStatus.SCREENING, InterviewStage.INITIAL),
                Arguments.of(ApplicantStatus.FOR_FINAL_INTERVIEW, InterviewStage.FINAL),
                Arguments.of(ApplicantStatus.FOR_CLIENT_INTERVIEW, InterviewStage.CLIENT)
        );
    }
}
