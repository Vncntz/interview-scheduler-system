package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.service.BookingStageEligibilityPolicy;
import com.company.iss.dashboard.dto.FollowUpApplicant;
import com.company.iss.dashboard.dto.FollowUpDeadlineFilter;
import com.company.iss.dashboard.dto.FollowUpQueueSummary;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import com.company.iss.dashboard.repository.FollowUpApplicantProjection;
import com.company.iss.dashboard.repository.FollowUpWorkloadProjection;
import com.company.iss.dashboard.repository.RecruiterFollowUpRepository;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class RecruiterWorkbenchService {

    private static final int MAX_FOLLOW_UP_PAGE_SIZE = 100;
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED
    );
    private static final List<BookingStatus> FOLLOW_UP_ACTIVE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );
    private static final List<BookingStatus> REPLACEMENT_STATUSES = List.of(
            BookingStatus.CANCELLED, BookingStatus.NO_SHOW
    );
    private static final List<BookingStatus> TODAY_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.ATTENDED
    );

    private final BookingRepository bookingRepository;
    private final RecruiterFollowUpRepository followUpRepository;
    private final SecurityService securityService;
    private final FollowUpSlaPolicy followUpSlaPolicy;
    private final BookingStageEligibilityPolicy stageEligibilityPolicy = new BookingStageEligibilityPolicy();

    public RecruiterWorkbenchService(
            BookingRepository bookingRepository,
            RecruiterFollowUpRepository followUpRepository,
            SecurityService securityService,
            FollowUpSlaPolicy followUpSlaPolicy
    ) {
        this.bookingRepository = bookingRepository;
        this.followUpRepository = followUpRepository;
        this.securityService = securityService;
        this.followUpSlaPolicy = followUpSlaPolicy;
    }

    @Transactional(readOnly = true)
    public RecruiterWorkbenchData load() {
        User actor = requireRecruiter();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        Long branchId = actor.getBranch().getId();
        LocalDateTime calculatedAt = followUpSlaPolicy.now();

        return new RecruiterWorkbenchData(
                map(bookingRepository.findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
                        actor.getId(), today, TODAY_STATUSES
                )),
                map(bookingRepository.findUpcomingAssigned(actor.getId(), today, now, ACTIVE_STATUSES)),
                map(bookingRepository.findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                        branchId, BookingStatus.BOOKED
                )),
                map(bookingRepository.findDueByBranchAndStatus(
                        branchId, BookingStatus.CONFIRMED, today, now
                )),
                map(bookingRepository.findOverdueUnevaluatedByBranch(
                        branchId, BookingStatus.ATTENDED, today, now
                )),
                summarize(branchId, InterviewStage.FINAL, calculatedAt),
                summarize(branchId, InterviewStage.CLIENT, calculatedAt)
        );
    }

    @Transactional(readOnly = true)
    public List<FollowUpApplicant> findFollowUpPage(
            InterviewStage stage,
            FollowUpDeadlineFilter deadlineFilter,
            int offset,
            int limit
    ) {
        User actor = requireRecruiter();
        FollowUpStageRule rule = ruleFor(stage);
        FollowUpDeadlineFilter validatedFilter = requireDeadlineFilter(deadlineFilter);
        validateFollowUpWindow(offset, limit);
        LocalDateTime calculatedAt = followUpSlaPolicy.now();
        DeadlineCutoffs cutoffs = cutoffs(stage, calculatedAt);
        return followUpRepository.findFollowUpsByStage(
                actor.getBranch().getId(), stage, rule.applicantStatus(), ApplicantStatus.SCHEDULED,
                rule.interviewResult(), REPLACEMENT_STATUSES, FOLLOW_UP_ACTIVE_STATUSES,
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                BookingLifecycleAction.ATTENDANCE_RECORDED,
                validatedFilter.name(), cutoffs.overdue(), cutoffs.dueSoon(),
                new OffsetLimitPageable(offset, limit)
        ).stream().map(projection -> toFollowUpDto(projection, stage, calculatedAt)).toList();
    }

    @Transactional(readOnly = true)
    public long countFollowUps(InterviewStage stage, FollowUpDeadlineFilter deadlineFilter) {
        User actor = requireRecruiter();
        FollowUpStageRule rule = ruleFor(stage);
        FollowUpDeadlineFilter validatedFilter = requireDeadlineFilter(deadlineFilter);
        DeadlineCutoffs cutoffs = cutoffs(stage, followUpSlaPolicy.now());
        return followUpRepository.countFollowUpsByStage(
                actor.getBranch().getId(), stage, rule.applicantStatus(), ApplicantStatus.SCHEDULED,
                rule.interviewResult(), REPLACEMENT_STATUSES, FOLLOW_UP_ACTIVE_STATUSES,
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                validatedFilter.name(), cutoffs.overdue(), cutoffs.dueSoon()
        );
    }

    @Transactional(readOnly = true)
    public FollowUpQueueSummary getFollowUpSummary(InterviewStage stage) {
        User actor = requireRecruiter();
        return summarize(actor.getBranch().getId(), stage, followUpSlaPolicy.now());
    }

    private FollowUpQueueSummary summarize(Long branchId, InterviewStage stage, LocalDateTime calculatedAt) {
        FollowUpStageRule rule = ruleFor(stage);
        Duration target = followUpSlaPolicy.targetFor(stage);
        DeadlineCutoffs cutoffs = cutoffs(stage, calculatedAt);
        FollowUpWorkloadProjection workload = followUpRepository.summarizeFollowUpsByStage(
                branchId, stage, rule.applicantStatus(), ApplicantStatus.SCHEDULED,
                rule.interviewResult(), REPLACEMENT_STATUSES, FOLLOW_UP_ACTIVE_STATUSES,
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                cutoffs.overdue(), cutoffs.dueSoon()
        );
        long onTrack = value(workload.getOnTrack());
        long dueSoon = value(workload.getDueSoon());
        long overdue = value(workload.getOverdue());
        long timingUnavailable = value(workload.getTimingUnavailable());
        long total = value(workload.getTotal());
        if (total != onTrack + dueSoon + overdue + timingUnavailable) {
            throw new IllegalStateException("Follow-up workload categories do not match the total.");
        }
        return new FollowUpQueueSummary(
                stage, target, total, onTrack, dueSoon, overdue, timingUnavailable, calculatedAt
        );
    }

    private DeadlineCutoffs cutoffs(InterviewStage stage, LocalDateTime calculatedAt) {
        Duration target = followUpSlaPolicy.targetFor(stage);
        return new DeadlineCutoffs(
                calculatedAt.minus(target),
                calculatedAt.minus(target.minus(followUpSlaPolicy.dueSoonWindow()))
        );
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private User requireRecruiter() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() != Role.RECRUITER
                || actor.getBranch() == null
                || actor.getBranch().getId() == null) {
            throw new AccessDeniedException("The recruiter workbench requires an assigned recruiter branch.");
        }
        return actor;
    }

    private void validateFollowUpWindow(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Follow-up offset must not be negative.");
        }
        if (limit < 1 || limit > MAX_FOLLOW_UP_PAGE_SIZE) {
            throw new IllegalArgumentException("Follow-up limit must be between 1 and "
                    + MAX_FOLLOW_UP_PAGE_SIZE + ".");
        }
    }

    private FollowUpDeadlineFilter requireDeadlineFilter(FollowUpDeadlineFilter deadlineFilter) {
        if (deadlineFilter == null) {
            throw new IllegalArgumentException("Follow-up deadline filter is required.");
        }
        return deadlineFilter;
    }

    private FollowUpStageRule ruleFor(InterviewStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException("Follow-up stage must be FINAL or CLIENT.");
        }
        return switch (stage) {
            case FINAL -> new FollowUpStageRule(
                    ApplicantStatus.FOR_FINAL_INTERVIEW, InterviewResult.FOR_FINAL_INTERVIEW
            );
            case CLIENT -> new FollowUpStageRule(
                    ApplicantStatus.FOR_CLIENT_INTERVIEW, InterviewResult.FOR_CLIENT_INTERVIEW
            );
            case INITIAL -> throw new IllegalArgumentException(
                    "Follow-up stage must be FINAL or CLIENT."
            );
        };
    }

    private List<WorkbenchInterview> map(List<Booking> bookings) {
        return bookings.stream().map(this::toDto).toList();
    }

    private WorkbenchInterview toDto(Booking booking) {
        return new WorkbenchInterview(
                booking.getId(),
                booking.getApplicant().getId(),
                booking.getBookingReference(),
                booking.getApplicant().getFullName(),
                booking.getApplicant().getPositionOpening() == null
                        ? "Unassigned"
                        : booking.getApplicant().getPositionOpening().getTitle(),
                booking.getSchedule().getScheduleDate(),
                booking.getSchedule().getStartTime(),
                booking.getSchedule().getEndTime(),
                booking.getSchedule().getRecruiter().getFullName(),
                booking.getInterviewStage(),
                booking.getStatus()
        );
    }

    private FollowUpApplicant toFollowUpDto(
            FollowUpApplicantProjection projection,
            InterviewStage requestedStage,
            LocalDateTime calculatedAt
    ) {
        InterviewStage requiredStage = stageEligibilityPolicy.requiredStage(
                projection.getApplicantStatus(),
                projection.getMostRecentBookingStatus(),
                projection.getMostRecentBookingStage()
        );
        if (requiredStage != requestedStage) {
            throw new IllegalStateException("Follow-up projection returned an applicant for the wrong stage.");
        }
        LocalDateTime waitingSince = projection.getWaitingSince();
        LocalDateTime relatedAppointmentAt = projection.getRelatedAppointmentDate() == null
                || projection.getRelatedAppointmentStartTime() == null
                ? null
                : LocalDateTime.of(
                        projection.getRelatedAppointmentDate(), projection.getRelatedAppointmentStartTime()
                );
        return new FollowUpApplicant(
                projection.getApplicantId(),
                projection.getBranchId(),
                projection.getApplicantName(),
                projection.getPositionTitle(),
                projection.getClientName(),
                requiredStage,
                relatedAppointmentAt,
                waitingSince,
                waitingSince == null ? null : followUpSlaPolicy.deadline(requiredStage, waitingSince),
                followUpSlaPolicy.elapsed(waitingSince, calculatedAt),
                waitingSince == null ? null : followUpSlaPolicy.status(requiredStage, waitingSince, calculatedAt)
        );
    }

    private record FollowUpStageRule(ApplicantStatus applicantStatus, InterviewResult interviewResult) {
    }

    private record DeadlineCutoffs(LocalDateTime overdue, LocalDateTime dueSoon) {
    }
}
