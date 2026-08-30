package com.company.iss.applicant.service;

import com.company.iss.applicant.dto.*;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.booking.service.BookingStageEligibilityPolicy;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicantJourneyService {

    private static final List<BookingStatus> CURRENT_APPOINTMENT_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("MMM d, uuuu h:mm a");

    private final ApplicantRepository applicantRepository;
    private final BookingRepository bookingRepository;
    private final BookingRescheduleHistoryRepository rescheduleRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final ApplicantHiringJourneyReader hiringJourneyReader;
    private final SecurityService securityService;
    private final BookingStageEligibilityPolicy stagePolicy = new BookingStageEligibilityPolicy();

    public ApplicantJourneyService(
            ApplicantRepository applicantRepository,
            BookingRepository bookingRepository,
            BookingRescheduleHistoryRepository rescheduleRepository,
            InterviewEvaluationRepository evaluationRepository,
            ApplicantHiringJourneyReader hiringJourneyReader,
            SecurityService securityService
    ) {
        this.applicantRepository = applicantRepository;
        this.bookingRepository = bookingRepository;
        this.rescheduleRepository = rescheduleRepository;
        this.evaluationRepository = evaluationRepository;
        this.hiringJourneyReader = hiringJourneyReader;
        this.securityService = securityService;
    }

    @Transactional(readOnly = true)
    public ApplicantProfile load(Long applicantId) {
        User actor = securityService.requireOperationsUser();
        Applicant applicant = requireAuthorizedApplicant(applicantId, actor);

        List<Booking> bookings = bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(applicantId);
        List<BookingRescheduleHistory> reschedules =
                rescheduleRepository.findByBookingApplicantIdOrderByRescheduledAtAscIdAsc(applicantId);
        List<InterviewEvaluation> evaluations =
                evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(applicantId);
        ApplicantHiringJourneyContribution hiring = hiringJourneyReader.read(applicantId);

        ApplicantSummary summary = toSummary(applicant);
        ApplicantRecruitmentState state = deriveState(applicant, bookings, evaluations, hiring);
        return new ApplicantProfile(
                summary,
                state,
                actions(applicant, state, bookings),
                timeline(applicant, bookings, reschedules, evaluations, hiring)
        );
    }

    private Applicant requireAuthorizedApplicant(Long applicantId, User actor) {
        if (applicantId == null) {
            throw new BusinessRuleViolationException("Applicant not found.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return applicantRepository.findDetailedById(applicantId)
                    .orElseThrow(() -> new BusinessRuleViolationException("Applicant not found."));
        }
        return applicantRepository.findDetailedByIdAndBranchId(applicantId, actor.getBranch().getId())
                .orElseThrow(() -> new AccessDeniedException("Applicant profile is not available."));
    }

    private ApplicantSummary toSummary(Applicant applicant) {
        var position = applicant.getPositionOpening();
        return new ApplicantSummary(
                applicant.getId(),
                applicant.getBranch() == null ? null : applicant.getBranch().getId(),
                safe(applicant.getFullName()),
                safe(applicant.getEmail()),
                safe(applicant.getMobileNumber()),
                applicant.getBranch() == null ? "" : safe(applicant.getBranch().getBranchName()),
                position == null ? "" : safe(position.getTitle()),
                position == null || position.getClient() == null ? "" : safe(position.getClient().getCompanyName()),
                position == null ? "" : safe(position.getWorkLocation()),
                applicant.getStatus(),
                applicant.isActive(),
                safe(applicant.getSource()),
                safe(applicant.getRemarks()),
                applicant.getCreatedAt()
        );
    }

    private ApplicantRecruitmentState deriveState(
            Applicant applicant,
            List<Booking> bookings,
            List<InterviewEvaluation> evaluations,
            ApplicantHiringJourneyContribution hiring
    ) {
        Booking latest = bookings.isEmpty() ? null : bookings.getLast();
        Booking current = bookings.stream()
                .filter(this::isCurrentAppointment)
                .max(Comparator.comparing(Booking::getBookedDateTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Booking::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        Booking stageSource = latest;
        if (List.of(ApplicantStatus.PASSED, ApplicantStatus.OFFERED, ApplicantStatus.HIRED,
                ApplicantStatus.OFFER_DECLINED, ApplicantStatus.WITHDRAWN).contains(applicant.getStatus())) {
            stageSource = bookings.reversed().stream()
                    .filter(booking -> booking.getStatus() == BookingStatus.PASSED)
                    .findFirst().orElse(latest);
        }
        InterviewStage currentStage = current != null
                ? current.getInterviewStage()
                : stageSource == null ? null : stageSource.getInterviewStage();
        InterviewStage nextStage = null;
        ApplicantNextAction nextAction = ApplicantNextAction.REVIEW;
        boolean complete = applicant.getStatus() == ApplicantStatus.HIRED;
        boolean closed = switch (applicant.getStatus()) {
            case FAILED, OFFER_DECLINED, WITHDRAWN -> true;
            default -> false;
        };

        if (complete) {
            nextAction = ApplicantNextAction.RECRUITMENT_COMPLETE;
        } else if (closed) {
            nextAction = ApplicantNextAction.RECRUITMENT_CLOSED;
        } else if (!applicant.isActive()) {
            nextAction = ApplicantNextAction.REVIEW;
        } else {
            switch (applicant.getStatus()) {
                case NEW, SCREENING, FOR_FINAL_INTERVIEW, FOR_CLIENT_INTERVIEW -> {
                    nextStage = stagePolicy.requiredStage(applicant.getStatus(), latest);
                    nextAction = ApplicantNextAction.SCHEDULE_INTERVIEW;
                }
                case SCHEDULED -> {
                    if (current != null) {
                        nextStage = current.getInterviewStage();
                        nextAction = switch (current.getStatus()) {
                            case BOOKED -> ApplicantNextAction.CONFIRM_INTERVIEW;
                            case CONFIRMED -> ApplicantNextAction.RECORD_ATTENDANCE;
                            case RESCHEDULED -> ApplicantNextAction.MANAGE_BOOKING;
                            default -> ApplicantNextAction.REVIEW;
                        };
                    } else {
                        try {
                            nextStage = stagePolicy.requiredStage(applicant.getStatus(), latest);
                            nextAction = ApplicantNextAction.SCHEDULE_INTERVIEW;
                        } catch (BusinessRuleViolationException ignored) {
                            nextAction = ApplicantNextAction.REVIEW;
                        }
                    }
                }
                case INTERVIEWED -> {
                    Set<Long> evaluatedBookingIds = new HashSet<>();
                    evaluations.stream().map(InterviewEvaluation::getBooking)
                            .filter(java.util.Objects::nonNull).map(Booking::getId).forEach(evaluatedBookingIds::add);
                    if (latest != null && latest.getStatus() == BookingStatus.ATTENDED
                            && !evaluatedBookingIds.contains(latest.getId())) {
                        nextAction = ApplicantNextAction.EVALUATE_INTERVIEW;
                    }
                }
                case PASSED -> nextAction = hiring.offerEligible()
                        ? ApplicantNextAction.ISSUE_JOB_OFFER : ApplicantNextAction.REVIEW;
                case OFFERED -> nextAction = hiring.outstandingOffer()
                        ? ApplicantNextAction.RECORD_OFFER_DECISION : ApplicantNextAction.REVIEW;
                case ON_HOLD -> nextAction = ApplicantNextAction.REVIEW;
                case HIRED, FAILED, OFFER_DECLINED, WITHDRAWN -> { }
            }
        }
        return new ApplicantRecruitmentState(
                applicant.getStatus(), currentStage, nextStage, nextAction,
                current == null ? null : toInterviewSummary(current), complete, closed
        );
    }

    private boolean isCurrentAppointment(Booking booking) {
        Schedule schedule = booking.getSchedule();
        return CURRENT_APPOINTMENT_STATUSES.contains(booking.getStatus())
                && schedule != null && schedule.isActive() && schedule.getStatus() != ScheduleStatus.CANCELLED;
    }

    private ApplicantInterviewSummary toInterviewSummary(Booking booking) {
        Schedule schedule = booking.getSchedule();
        return new ApplicantInterviewSummary(
                booking.getId(), booking.getBookingReference(), booking.getInterviewStage(),
                schedule.getScheduleDate(), schedule.getStartTime(), schedule.getEndTime(), schedule.getInterviewMode(),
                schedule.getRecruiter() == null ? "" : safe(schedule.getRecruiter().getFullName()), booking.getStatus()
        );
    }

    private List<ApplicantProfileAction> actions(
            Applicant applicant,
            ApplicantRecruitmentState state,
            List<Booking> bookings
    ) {
        if (!applicant.isActive()) {
            return List.of();
        }
        Long applicantId = applicant.getId();
        return switch (state.nextAction()) {
            case SCHEDULE_INTERVIEW -> List.of(new ApplicantProfileAction(
                    ApplicantProfileActionType.SCHEDULE_INTERVIEW,
                    "Schedule " + state.nextRequiredStage().name() + " Interview",
                    applicantId, null, state.nextRequiredStage()));
            case CONFIRM_INTERVIEW, RECORD_ATTENDANCE, MANAGE_BOOKING -> List.of(new ApplicantProfileAction(
                    ApplicantProfileActionType.VIEW_BOOKING, "View Booking", applicantId,
                    state.currentInterview().bookingId(), state.currentInterview().interviewStage()));
            case EVALUATE_INTERVIEW -> List.of(new ApplicantProfileAction(
                    ApplicantProfileActionType.EVALUATE_INTERVIEW, "Evaluate Interview", applicantId,
                    latestAttendedBookingId(bookings), state.currentStage()));
            case ISSUE_JOB_OFFER -> List.of(new ApplicantProfileAction(
                    ApplicantProfileActionType.OPEN_HIRING, "Issue Job Offer", applicantId, null, state.currentStage()));
            case RECORD_OFFER_DECISION -> List.of(new ApplicantProfileAction(
                    ApplicantProfileActionType.OPEN_HIRING, "Record Offer Decision", applicantId, null, state.currentStage()));
            default -> List.of();
        };
    }

    private Long latestAttendedBookingId(List<Booking> bookings) {
        return bookings.reversed().stream()
                .filter(booking -> booking.getStatus() == BookingStatus.ATTENDED)
                .map(Booking::getId)
                .findFirst()
                .orElse(null);
    }

    private List<RecruitmentTimelineItem> timeline(
            Applicant applicant,
            List<Booking> bookings,
            List<BookingRescheduleHistory> reschedules,
            List<InterviewEvaluation> evaluations,
            ApplicantHiringJourneyContribution hiring
    ) {
        List<TimelineEntry> entries = new ArrayList<>();
        add(entries, applicant.getCreatedAt(), 0, applicant.getId(), RecruitmentTimelineEvent.APPLICATION_CREATED,
                "Application created", applicationDescription(applicant), null);

        for (Booking booking : bookings) {
            add(entries, booking.getBookedDateTime(), 1, booking.getId(), RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                    booking.getInterviewStage().name() + " interview booked",
                    bookingDescription(booking), booking.getInterviewStage());
        }
        for (BookingRescheduleHistory history : reschedules) {
            Booking booking = history.getBooking();
            InterviewStage stage = booking == null ? null : booking.getInterviewStage();
            add(entries, history.getRescheduledAt(), 2, history.getId(), RecruitmentTimelineEvent.INTERVIEW_RESCHEDULED,
                    (stage == null ? "Interview" : stage.name() + " interview") + " rescheduled",
                    rescheduleDescription(history), stage);
        }
        for (InterviewEvaluation evaluation : evaluations) {
            Booking booking = evaluation.getBooking();
            InterviewStage stage = booking == null ? null : booking.getInterviewStage();
            add(entries, evaluation.getEvaluationDate(), 3, evaluation.getId(), RecruitmentTimelineEvent.INTERVIEW_EVALUATED,
                    (stage == null ? "Interview" : stage.name() + " interview") + " evaluated",
                    evaluationDescription(evaluation), stage);
        }
        for (ApplicantHiringJourneyEvent event : hiring.events()) {
            RecruitmentTimelineEvent timelineEvent = RecruitmentTimelineEvent.valueOf(event.type().name());
            add(entries, event.occurredAt(), 4, event.sourceId(), timelineEvent,
                    hiringTitle(timelineEvent), hiringDescription(event), null);
        }
        return entries.stream()
                .sorted(Comparator.comparing(TimelineEntry::occurredAt)
                        .thenComparingInt(TimelineEntry::precedence)
                        .thenComparing(TimelineEntry::sourceId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(entry -> entry.item().event().ordinal()))
                .map(TimelineEntry::item)
                .toList();
    }

    private void add(List<TimelineEntry> entries, LocalDateTime occurredAt, int precedence, Long sourceId,
                     RecruitmentTimelineEvent event, String title, String description, InterviewStage stage) {
        if (occurredAt != null) {
            entries.add(new TimelineEntry(occurredAt, precedence, sourceId,
                    new RecruitmentTimelineItem(event, occurredAt, title, description, stage)));
        }
    }

    private String applicationDescription(Applicant applicant) {
        String position = applicant.getPositionOpening() == null ? "Unassigned position" : applicant.getPositionOpening().getTitle();
        String branch = applicant.getBranch() == null ? "Unassigned branch" : applicant.getBranch().getBranchName();
        return position + " · " + branch + (blank(applicant.getSource()) ? "" : " · Source: " + applicant.getSource());
    }

    private String bookingDescription(Booking booking) {
        Schedule schedule = booking.getSchedule();
        String slot = schedule == null ? "Schedule unavailable" : formatSlot(schedule);
        return slot + " · Reference: " + safe(booking.getBookingReference());
    }

    private String rescheduleDescription(BookingRescheduleHistory history) {
        String previous = history.getSourceSchedule() == null ? "Unavailable" : formatSlot(history.getSourceSchedule());
        String next = history.getDestinationSchedule() == null ? "Unavailable" : formatSlot(history.getDestinationSchedule());
        return "Previous: " + previous + " · New: " + next
                + (blank(history.getReason()) ? "" : " · Reason: " + history.getReason());
    }

    private String evaluationDescription(InterviewEvaluation evaluation) {
        return "Result: " + evaluation.getResult().name()
                + " · Communication: " + evaluation.getCommunicationScore() + "/10"
                + " · Technical: " + evaluation.getTechnicalScore() + "/10"
                + " · Attitude: " + evaluation.getAttitudeScore() + "/10"
                + (evaluation.getEvaluator() == null ? "" : " · Evaluator: " + evaluation.getEvaluator().getFullName())
                + (blank(evaluation.getRemarks()) ? "" : " · Remarks: " + evaluation.getRemarks());
    }

    private String hiringTitle(RecruitmentTimelineEvent event) {
        return switch (event) {
            case JOB_OFFERED -> "Job offer issued";
            case HIRED -> "Applicant hired";
            case OFFER_DECLINED -> "Offer declined";
            case WITHDRAWN -> "Applicant withdrawn";
            default -> event.name();
        };
    }

    private String hiringDescription(ApplicantHiringJourneyEvent event) {
        return (blank(event.actor()) ? "" : "Recorded by: " + event.actor())
                + (blank(event.remarks()) ? "" : (blank(event.actor()) ? "" : " · ") + "Remarks: " + event.remarks());
    }

    private String formatSlot(Schedule schedule) {
        return LocalDateTime.of(schedule.getScheduleDate(), schedule.getStartTime()).format(SLOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record TimelineEntry(
            LocalDateTime occurredAt,
            int precedence,
            Long sourceId,
            RecruitmentTimelineItem item
    ) {
    }
}
