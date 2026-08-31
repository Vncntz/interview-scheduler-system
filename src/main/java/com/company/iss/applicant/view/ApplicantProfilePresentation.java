package com.company.iss.applicant.view;

import com.company.iss.applicant.dto.ApplicantNextAction;
import com.company.iss.applicant.dto.ApplicantProfileAction;
import com.company.iss.applicant.dto.ApplicantProfileActionType;
import com.company.iss.applicant.dto.RecruitmentTimelineEvent;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.schedule.entity.InterviewMode;

import java.util.Locale;

final class ApplicantProfilePresentation {

    private static final String NOT_AVAILABLE = "Not available";

    private ApplicantProfilePresentation() {
    }

    static String applicantStatusLabel(ApplicantStatus status) {
        if (status == null) {
            return NOT_AVAILABLE;
        }
        return switch (status) {
            case NEW -> "New";
            case SCREENING -> "Screening";
            case SCHEDULED -> "Scheduled";
            case INTERVIEWED -> "Interviewed";
            case PASSED -> "Passed";
            case OFFERED -> "Offered";
            case OFFER_DECLINED -> "Offer Declined";
            case FAILED -> "Failed";
            case WITHDRAWN -> "Withdrawn";
            case HIRED -> "Hired";
            case FOR_FINAL_INTERVIEW -> "For Final Interview";
            case FOR_CLIENT_INTERVIEW -> "For Client Interview";
            case ON_HOLD -> "On Hold";
        };
    }

    static String interviewStageLabel(InterviewStage stage) {
        if (stage == null) {
            return NOT_AVAILABLE;
        }
        return switch (stage) {
            case INITIAL -> "Initial Interview";
            case FINAL -> "Final Interview";
            case CLIENT -> "Client Interview";
        };
    }

    static String bookingStatusLabel(BookingStatus status) {
        if (status == null) {
            return NOT_AVAILABLE;
        }
        return switch (status) {
            case BOOKED -> "Booked";
            case CONFIRMED -> "Confirmed";
            case ATTENDED -> "Attended";
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case NO_SHOW -> "No Show";
            case CANCELLED -> "Cancelled";
            case RESCHEDULED -> "Rescheduled";
            case FOR_FINAL_INTERVIEW -> "For Final Interview";
            case FOR_CLIENT_INTERVIEW -> "For Client Interview";
            case ON_HOLD -> "On Hold";
        };
    }

    static String interviewModeLabel(InterviewMode mode) {
        if (mode == null) {
            return NOT_AVAILABLE;
        }
        return switch (mode) {
            case ONSITE -> "On-site";
            case ONLINE -> "Online";
            case PHONE -> "Phone";
        };
    }

    static String actionLabel(ApplicantProfileAction action) {
        if (action == null) {
            return "Review Applicant";
        }
        if (action.type() == null) {
            return "Review Applicant";
        }
        return switch (action.type()) {
            case SCHEDULE_INTERVIEW -> actionTypeLabel(action.type(), action.interviewStage());
            case VIEW_BOOKING -> "View Booking";
            case EVALUATE_INTERVIEW -> "Evaluate Interview";
            case OPEN_HIRING -> {
                String suppliedLabel = trimmed(action.label());
                if ("Record Offer Decision".equals(suppliedLabel)) {
                    yield "Record Offer Decision";
                }
                if ("Issue Job Offer".equals(suppliedLabel)) {
                    yield "Issue Job Offer";
                }
                yield "Open Hiring";
            }
        };
    }

    static String actionTypeLabel(ApplicantProfileActionType type, InterviewStage stage) {
        if (type == null) {
            return "Review Applicant";
        }
        return switch (type) {
            case SCHEDULE_INTERVIEW -> stage == null
                    ? "Schedule Interview"
                    : "Schedule " + stageName(stage) + " Interview";
            case VIEW_BOOKING -> "View Booking";
            case EVALUATE_INTERVIEW -> "Evaluate Interview";
            case OPEN_HIRING -> "Open Hiring";
        };
    }

    static String nextStep(ApplicantNextAction action, InterviewStage nextStage) {
        if (action == null) {
            return "Review the applicant's current recruitment state.";
        }
        return switch (action) {
            case SCHEDULE_INTERVIEW -> nextStage == null
                    ? "Schedule the applicant's next interview."
                    : "Schedule the applicant's " + stageName(nextStage).toLowerCase(Locale.ROOT) + " interview.";
            case CONFIRM_INTERVIEW -> "Confirm the current interview booking.";
            case RECORD_ATTENDANCE -> "Record whether the applicant attended the interview.";
            case MANAGE_BOOKING -> "Review or manage the current interview booking.";
            case EVALUATE_INTERVIEW -> "Complete the interview evaluation.";
            case ISSUE_JOB_OFFER -> "Issue a job offer to the applicant.";
            case RECORD_OFFER_DECISION -> "Record the applicant's offer decision.";
            case REVIEW -> "Review the applicant's current recruitment state.";
            case RECRUITMENT_COMPLETE -> "Recruitment is complete.";
            case RECRUITMENT_CLOSED -> "Recruitment is closed.";
        };
    }

    static String timelineTitle(RecruitmentTimelineEvent event) {
        if (event == null) {
            return "Recruitment Update";
        }
        return switch (event) {
            case APPLICATION_CREATED -> "Application Created";
            case INTERVIEW_BOOKED -> "Interview Booked";
            case INTERVIEW_RESCHEDULED -> "Interview Rescheduled";
            case INTERVIEW_EVALUATED -> "Interview Evaluated";
            case JOB_OFFERED -> "Job Offer Issued";
            case HIRED -> "Applicant Hired";
            case OFFER_DECLINED -> "Offer Declined";
            case WITHDRAWN -> "Applicant Withdrawn";
        };
    }

    static String timelineFamily(RecruitmentTimelineEvent event) {
        if (event == null) {
            return "hiring";
        }
        return switch (event) {
            case APPLICATION_CREATED -> "application";
            case INTERVIEW_BOOKED, INTERVIEW_RESCHEDULED -> "interview";
            case INTERVIEW_EVALUATED -> "evaluation";
            case JOB_OFFERED, HIRED, OFFER_DECLINED, WITHDRAWN -> "hiring";
        };
    }

    static String applicantStatusTone(ApplicantStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case NEW, SCREENING -> "neutral";
            case SCHEDULED, INTERVIEWED, OFFERED -> "accent";
            case FOR_FINAL_INTERVIEW, FOR_CLIENT_INTERVIEW, ON_HOLD -> "warning";
            case PASSED, HIRED -> "success";
            case FAILED, OFFER_DECLINED, WITHDRAWN -> "danger";
        };
    }

    static String bookingStatusTone(BookingStatus status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case BOOKED, CONFIRMED, ATTENDED, RESCHEDULED -> "accent";
            case FOR_FINAL_INTERVIEW, FOR_CLIENT_INTERVIEW, ON_HOLD -> "warning";
            case PASSED -> "success";
            case FAILED, NO_SHOW, CANCELLED -> "danger";
        };
    }

    static String initials(String name) {
        String normalized = normalizeWhitespace(name);
        if (normalized.isEmpty()) {
            return "AP";
        }
        String[] tokens = normalized.split(" ");
        StringBuilder initials = new StringBuilder(2);
        appendUppercaseCodePoint(initials, tokens[0]);
        if (tokens.length > 1) {
            appendUppercaseCodePoint(initials, tokens[tokens.length - 1]);
        }
        return initials.toString();
    }

    private static String stageName(InterviewStage stage) {
        return switch (stage) {
            case INITIAL -> "Initial";
            case FINAL -> "Final";
            case CLIENT -> "Client";
        };
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }
        return normalized.toString();
    }

    private static void appendUppercaseCodePoint(StringBuilder target, String token) {
        target.appendCodePoint(Character.toUpperCase(token.codePointAt(0)));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
