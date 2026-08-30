package com.company.iss.booking.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.shared.exception.BusinessRuleViolationException;
public class BookingStageEligibilityPolicy {

    public InterviewStage requiredStage(ApplicantStatus applicantStatus, Booking mostRecentBooking) {
        if (applicantStatus == null) {
            throw new BusinessRuleViolationException("Applicant status is required before booking an interview.");
        }

        return switch (applicantStatus) {
            case NEW, SCREENING -> InterviewStage.INITIAL;
            case FOR_FINAL_INTERVIEW -> InterviewStage.FINAL;
            case FOR_CLIENT_INTERVIEW -> InterviewStage.CLIENT;
            case SCHEDULED -> replacementStage(mostRecentBooking);
            case INTERVIEWED, ON_HOLD, PASSED, FAILED, OFFERED, HIRED, OFFER_DECLINED, WITHDRAWN ->
                    throw new BusinessRuleViolationException(
                            "Applicant status " + applicantStatus.name() + " is not eligible for a new interview."
                    );
        };
    }

    public InterviewStage validateRequestedStage(
            ApplicantStatus applicantStatus,
            Booking mostRecentBooking,
            InterviewStage requestedStage
    ) {
        if (requestedStage == null) {
            throw new BusinessRuleViolationException("Interview stage is required.");
        }
        InterviewStage requiredStage = requiredStage(applicantStatus, mostRecentBooking);
        if (requestedStage != requiredStage) {
            throw new BusinessRuleViolationException(
                    "Applicant is eligible only for a " + requiredStage.name() + " interview."
            );
        }
        return requiredStage;
    }

    private InterviewStage replacementStage(Booking mostRecentBooking) {
        if (mostRecentBooking == null
                || (mostRecentBooking.getStatus() != BookingStatus.CANCELLED
                    && mostRecentBooking.getStatus() != BookingStatus.NO_SHOW)
                || mostRecentBooking.getInterviewStage() == null) {
            throw new BusinessRuleViolationException(
                    "Scheduled applicants can only be rebooked after a cancelled or missed interview."
            );
        }
        return mostRecentBooking.getInterviewStage();
    }
}
