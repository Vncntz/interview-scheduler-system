package com.company.iss.dashboard.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.service.BookingStageEligibilityPolicy;
import com.company.iss.dashboard.dto.FollowUpApplicant;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import com.company.iss.dashboard.repository.FollowUpApplicantProjection;
import com.company.iss.dashboard.repository.RecruiterFollowUpRepository;
import com.company.iss.evaluation.entity.InterviewResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class RecruiterWorkbenchService {

    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED
    );
    private static final List<BookingStatus> FOLLOW_UP_ACTIVE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
    );
    private static final List<BookingStatus> TODAY_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.ATTENDED
    );

    private final BookingRepository bookingRepository;
    private final RecruiterFollowUpRepository followUpRepository;
    private final SecurityService securityService;
    private final BookingStageEligibilityPolicy stageEligibilityPolicy = new BookingStageEligibilityPolicy();

    public RecruiterWorkbenchService(
            BookingRepository bookingRepository,
            RecruiterFollowUpRepository followUpRepository,
            SecurityService securityService
    ) {
        this.bookingRepository = bookingRepository;
        this.followUpRepository = followUpRepository;
        this.securityService = securityService;
    }

    @Transactional(readOnly = true)
    public RecruiterWorkbenchData load() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException("The recruiter workbench is only available to recruiters.");
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        Long branchId = actor.getBranch().getId();
        List<FollowUpApplicant> followUps = followUpRepository.findFollowUps(
                branchId,
                ApplicantStatus.FOR_FINAL_INTERVIEW,
                ApplicantStatus.FOR_CLIENT_INTERVIEW,
                ApplicantStatus.SCHEDULED,
                List.of(ApplicantStatus.FOR_FINAL_INTERVIEW, ApplicantStatus.FOR_CLIENT_INTERVIEW),
                InterviewResult.FOR_FINAL_INTERVIEW,
                InterviewResult.FOR_CLIENT_INTERVIEW,
                List.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
                List.of(InterviewStage.FINAL, InterviewStage.CLIENT),
                FOLLOW_UP_ACTIVE_STATUSES
        ).stream().map(this::toFollowUpDto).toList();

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
                followUps.stream()
                        .filter(item -> item.requiredStage() == InterviewStage.FINAL)
                        .toList(),
                followUps.stream()
                        .filter(item -> item.requiredStage() == InterviewStage.CLIENT)
                        .toList()
        );
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

    private FollowUpApplicant toFollowUpDto(FollowUpApplicantProjection projection) {
        InterviewStage requiredStage = stageEligibilityPolicy.requiredStage(
                projection.getApplicantStatus(),
                projection.getMostRecentBookingStatus(),
                projection.getMostRecentBookingStage()
        );
        return new FollowUpApplicant(
                projection.getApplicantId(),
                projection.getBranchId(),
                projection.getApplicantName(),
                projection.getPositionTitle(),
                projection.getClientName(),
                requiredStage,
                projection.getWaitingSince(),
                projection.getWaitingSince()
        );
    }
}
