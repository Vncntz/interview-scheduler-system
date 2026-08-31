package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.ApplicantNextAction;
import com.company.iss.applicant.dto.ApplicantProfileAction;
import com.company.iss.applicant.dto.ApplicantProfileActionType;
import com.company.iss.applicant.dto.RecruitmentTimelineEvent;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.schedule.entity.InterviewMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicantProfilePresentationTest {

    @Test
    void labelsEveryApplicantStatus() {
        Map<ApplicantStatus, String> expected = Map.ofEntries(
                Map.entry(ApplicantStatus.NEW, "New"),
                Map.entry(ApplicantStatus.SCREENING, "Screening"),
                Map.entry(ApplicantStatus.SCHEDULED, "Scheduled"),
                Map.entry(ApplicantStatus.INTERVIEWED, "Interviewed"),
                Map.entry(ApplicantStatus.PASSED, "Passed"),
                Map.entry(ApplicantStatus.OFFERED, "Offered"),
                Map.entry(ApplicantStatus.OFFER_DECLINED, "Offer Declined"),
                Map.entry(ApplicantStatus.FAILED, "Failed"),
                Map.entry(ApplicantStatus.WITHDRAWN, "Withdrawn"),
                Map.entry(ApplicantStatus.HIRED, "Hired"),
                Map.entry(ApplicantStatus.FOR_FINAL_INTERVIEW, "For Final Interview"),
                Map.entry(ApplicantStatus.FOR_CLIENT_INTERVIEW, "For Client Interview"),
                Map.entry(ApplicantStatus.ON_HOLD, "On Hold")
        );

        expected.forEach((status, label) -> assertEquals(label,
                ApplicantProfilePresentation.applicantStatusLabel(status)));
        assertEquals(ApplicantStatus.values().length, expected.size());
        assertEquals("Not available", ApplicantProfilePresentation.applicantStatusLabel(null));
    }

    @Test
    void labelsEveryInterviewStageBookingStatusAndMode() {
        assertEquals("Initial Interview", ApplicantProfilePresentation.interviewStageLabel(InterviewStage.INITIAL));
        assertEquals("Final Interview", ApplicantProfilePresentation.interviewStageLabel(InterviewStage.FINAL));
        assertEquals("Client Interview", ApplicantProfilePresentation.interviewStageLabel(InterviewStage.CLIENT));
        assertEquals("Not available", ApplicantProfilePresentation.interviewStageLabel(null));

        Map<BookingStatus, String> bookingLabels = Map.ofEntries(
                Map.entry(BookingStatus.BOOKED, "Booked"),
                Map.entry(BookingStatus.CONFIRMED, "Confirmed"),
                Map.entry(BookingStatus.ATTENDED, "Attended"),
                Map.entry(BookingStatus.PASSED, "Passed"),
                Map.entry(BookingStatus.FAILED, "Failed"),
                Map.entry(BookingStatus.NO_SHOW, "No Show"),
                Map.entry(BookingStatus.CANCELLED, "Cancelled"),
                Map.entry(BookingStatus.RESCHEDULED, "Rescheduled"),
                Map.entry(BookingStatus.FOR_FINAL_INTERVIEW, "For Final Interview"),
                Map.entry(BookingStatus.FOR_CLIENT_INTERVIEW, "For Client Interview"),
                Map.entry(BookingStatus.ON_HOLD, "On Hold")
        );
        bookingLabels.forEach((status, label) -> assertEquals(label,
                ApplicantProfilePresentation.bookingStatusLabel(status)));
        assertEquals(BookingStatus.values().length, bookingLabels.size());
        assertEquals("Not available", ApplicantProfilePresentation.bookingStatusLabel(null));

        assertEquals("On-site", ApplicantProfilePresentation.interviewModeLabel(InterviewMode.ONSITE));
        assertEquals("Online", ApplicantProfilePresentation.interviewModeLabel(InterviewMode.ONLINE));
        assertEquals("Phone", ApplicantProfilePresentation.interviewModeLabel(InterviewMode.PHONE));
        assertEquals("Not available", ApplicantProfilePresentation.interviewModeLabel(null));
    }

    @Test
    void labelsEveryActionTypeAndUsesTheSuppliedStage() {
        assertEquals("Schedule Initial Interview", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, InterviewStage.INITIAL));
        assertEquals("Schedule Final Interview", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, InterviewStage.FINAL));
        assertEquals("Schedule Client Interview", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, InterviewStage.CLIENT));
        assertEquals("Schedule Interview", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, null));
        assertEquals("View Booking", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.VIEW_BOOKING, null));
        assertEquals("Evaluate Interview", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.EVALUATE_INTERVIEW, null));
        assertEquals("Open Hiring", ApplicantProfilePresentation.actionTypeLabel(
                ApplicantProfileActionType.OPEN_HIRING, null));
        assertEquals("Review Applicant", ApplicantProfilePresentation.actionTypeLabel(null, null));

        ApplicantProfileAction schedule = new ApplicantProfileAction(
                ApplicantProfileActionType.SCHEDULE_INTERVIEW, "ignored raw label", 1L, null,
                InterviewStage.FINAL);
        ApplicantProfileAction offer = new ApplicantProfileAction(
                ApplicantProfileActionType.OPEN_HIRING, "  Issue Job Offer  ", 1L, null, null);
        assertEquals("Schedule Final Interview", ApplicantProfilePresentation.actionLabel(schedule));
        assertEquals("Issue Job Offer", ApplicantProfilePresentation.actionLabel(offer));
        assertEquals("View Booking", ApplicantProfilePresentation.actionLabel(new ApplicantProfileAction(
                ApplicantProfileActionType.VIEW_BOOKING, "VIEW_BOOKING", 1L, 2L, null)));
        assertEquals("Evaluate Interview", ApplicantProfilePresentation.actionLabel(new ApplicantProfileAction(
                ApplicantProfileActionType.EVALUATE_INTERVIEW, "EVALUATE_INTERVIEW", 1L, 2L, null)));
        assertEquals("Open Hiring", ApplicantProfilePresentation.actionLabel(new ApplicantProfileAction(
                ApplicantProfileActionType.OPEN_HIRING, "OPEN_HIRING", 1L, null, null)));
        assertEquals("Review Applicant", ApplicantProfilePresentation.actionLabel(null));
    }

    @Test
    void describesEveryNextActionWithoutRawEnumText() {
        Map<ApplicantNextAction, String> expected = Map.ofEntries(
                Map.entry(ApplicantNextAction.CONFIRM_INTERVIEW, "Confirm the current interview booking."),
                Map.entry(ApplicantNextAction.RECORD_ATTENDANCE,
                        "Record whether the applicant attended the interview."),
                Map.entry(ApplicantNextAction.MANAGE_BOOKING,
                        "Review or manage the current interview booking."),
                Map.entry(ApplicantNextAction.EVALUATE_INTERVIEW, "Complete the interview evaluation."),
                Map.entry(ApplicantNextAction.ISSUE_JOB_OFFER, "Issue a job offer to the applicant."),
                Map.entry(ApplicantNextAction.RECORD_OFFER_DECISION,
                        "Record the applicant's offer decision."),
                Map.entry(ApplicantNextAction.REVIEW,
                        "Review the applicant's current recruitment state."),
                Map.entry(ApplicantNextAction.RECRUITMENT_COMPLETE, "Recruitment is complete."),
                Map.entry(ApplicantNextAction.RECRUITMENT_CLOSED, "Recruitment is closed.")
        );
        expected.forEach((action, description) -> assertEquals(description,
                ApplicantProfilePresentation.nextStep(action, null)));
        assertEquals("Schedule the applicant's final interview.", ApplicantProfilePresentation.nextStep(
                ApplicantNextAction.SCHEDULE_INTERVIEW, InterviewStage.FINAL));
        assertEquals("Schedule the applicant's next interview.", ApplicantProfilePresentation.nextStep(
                ApplicantNextAction.SCHEDULE_INTERVIEW, null));
        assertEquals("Review the applicant's current recruitment state.",
                ApplicantProfilePresentation.nextStep(null, InterviewStage.CLIENT));
        assertEquals(ApplicantNextAction.values().length - 1, expected.size());
    }

    @Test
    void labelsAndGroupsEveryTimelineEvent() {
        Map<RecruitmentTimelineEvent, String> titles = Map.of(
                RecruitmentTimelineEvent.APPLICATION_CREATED, "Application Created",
                RecruitmentTimelineEvent.INTERVIEW_BOOKED, "Interview Booked",
                RecruitmentTimelineEvent.INTERVIEW_RESCHEDULED, "Interview Rescheduled",
                RecruitmentTimelineEvent.INTERVIEW_EVALUATED, "Interview Evaluated",
                RecruitmentTimelineEvent.JOB_OFFERED, "Job Offer Issued",
                RecruitmentTimelineEvent.HIRED, "Applicant Hired",
                RecruitmentTimelineEvent.OFFER_DECLINED, "Offer Declined",
                RecruitmentTimelineEvent.WITHDRAWN, "Applicant Withdrawn"
        );
        Map<RecruitmentTimelineEvent, String> families = Map.of(
                RecruitmentTimelineEvent.APPLICATION_CREATED, "application",
                RecruitmentTimelineEvent.INTERVIEW_BOOKED, "interview",
                RecruitmentTimelineEvent.INTERVIEW_RESCHEDULED, "interview",
                RecruitmentTimelineEvent.INTERVIEW_EVALUATED, "evaluation",
                RecruitmentTimelineEvent.JOB_OFFERED, "hiring",
                RecruitmentTimelineEvent.HIRED, "hiring",
                RecruitmentTimelineEvent.OFFER_DECLINED, "hiring",
                RecruitmentTimelineEvent.WITHDRAWN, "hiring"
        );
        titles.forEach((event, title) -> assertEquals(title,
                ApplicantProfilePresentation.timelineTitle(event)));
        families.forEach((event, family) -> assertEquals(family,
                ApplicantProfilePresentation.timelineFamily(event)));
        assertEquals(RecruitmentTimelineEvent.values().length, titles.size());
        assertEquals(RecruitmentTimelineEvent.values().length, families.size());
        assertEquals("Recruitment Update", ApplicantProfilePresentation.timelineTitle(null));
        assertEquals("hiring", ApplicantProfilePresentation.timelineFamily(null));
    }

    @Test
    void producesSafeUnicodeInitials() {
        assertEquals("AP", ApplicantProfilePresentation.initials(null));
        assertEquals("AP", ApplicantProfilePresentation.initials("   \t\n "));
        assertEquals("A", ApplicantProfilePresentation.initials("alex"));
        assertEquals("AC", ApplicantProfilePresentation.initials("  alex   middle   candidate  "));
        assertEquals("AC", ApplicantProfilePresentation.initials("alex\u00a0candidate"));
        assertEquals("ÉD", ApplicantProfilePresentation.initials("élise dupont"));
        assertEquals("𐐒D", ApplicantProfilePresentation.initials("𐐺en deseret"));
        assertEquals("VL", ApplicantProfilePresentation.initials(
                "Verylongfirstnamethatmustnotoverflow Longlastnamethatmustnotoverflow"));
    }
}
