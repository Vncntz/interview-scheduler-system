package com.company.iss.applicant.service;

import com.company.iss.applicant.dto.ApplicantNextAction;
import com.company.iss.applicant.dto.RecruitmentTimelineEvent;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingLifecycleHistory;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingLifecycleHistoryRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.client.entity.Client;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicantJourneyServiceTest {

    @Mock ApplicantRepository applicantRepository;
    @Mock BookingRepository bookingRepository;
    @Mock BookingLifecycleHistoryRepository lifecycleRepository;
    @Mock BookingRescheduleHistoryRepository rescheduleRepository;
    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock ApplicantHiringJourneyReader hiringReader;
    @Mock SecurityService securityService;

    private ApplicantJourneyService service;

    @BeforeEach
    void setUp() {
        service = new ApplicantJourneyService(
                applicantRepository, bookingRepository, lifecycleRepository, rescheduleRepository,
                evaluationRepository, hiringReader, securityService
        );
        lenient().when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(anyLong()))
                .thenReturn(List.of());
        lenient().when(lifecycleRepository.findByBookingApplicantIdOrderByOccurredAtAscIdAsc(anyLong()))
                .thenReturn(List.of());
        lenient().when(rescheduleRepository.findByBookingApplicantIdOrderByRescheduledAtAscIdAsc(anyLong()))
                .thenReturn(List.of());
        lenient().when(evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(anyLong()))
                .thenReturn(List.of());
        lenient().when(hiringReader.read(anyLong())).thenReturn(ApplicantHiringJourneyContribution.empty());
    }

    @Test
    void adminLoadsNullSafeSummaryAndInitialNextAction() {
        Applicant applicant = applicant(ApplicantStatus.NEW);
        when(securityService.requireOperationsUser()).thenReturn(actor(Role.ADMIN, applicant.getBranch()));
        when(applicantRepository.findDetailedById(42L)).thenReturn(Optional.of(applicant));

        var profile = service.load(42L);

        assertEquals("Alex Candidate", profile.summary().fullName());
        assertEquals("Client", profile.summary().client());
        assertEquals(InterviewStage.INITIAL, profile.currentState().nextRequiredStage());
        assertEquals(ApplicantNextAction.SCHEDULE_INTERVIEW, profile.currentState().nextAction());
        assertEquals(RecruitmentTimelineEvent.APPLICATION_CREATED, profile.timeline().getFirst().event());
    }

    @Test
    void sameBranchRecruiterUsesApplicantAndBranchScopedRootQuery() {
        Applicant applicant = applicant(ApplicantStatus.FOR_FINAL_INTERVIEW);
        User recruiter = actor(Role.RECRUITER, applicant.getBranch());
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findDetailedByIdAndBranchId(42L, 7L)).thenReturn(Optional.of(applicant));

        var profile = service.load(42L);

        assertEquals(InterviewStage.FINAL, profile.currentState().nextRequiredStage());
        verify(applicantRepository).findDetailedByIdAndBranchId(42L, 7L);
    }

    @Test
    void deniedRecruiterStopsBeforeAnyRelatedHistoryQuery() {
        Branch branch = branch();
        when(securityService.requireOperationsUser()).thenReturn(actor(Role.RECRUITER, branch));
        when(applicantRepository.findDetailedByIdAndBranchId(99L, 7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.load(99L));

        verifyNoInteractions(bookingRepository, lifecycleRepository, rescheduleRepository, evaluationRepository, hiringReader);
    }

    @Test
    void currentAppointmentExcludesCancelledAndNoShowAndReplacementPreservesStage() {
        Applicant applicant = applicant(ApplicantStatus.SCHEDULED);
        Booking cancelled = booking(10L, applicant, InterviewStage.CLIENT, BookingStatus.CANCELLED,
                LocalDateTime.of(2026, 8, 20, 9, 0));
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L)).thenReturn(List.of(cancelled));

        var state = service.load(42L).currentState();

        assertNull(state.currentInterview());
        assertEquals(InterviewStage.CLIENT, state.nextRequiredStage());
        assertEquals(ApplicantNextAction.SCHEDULE_INTERVIEW, state.nextAction());
    }

    @Test
    void activeConfirmedBookingIsCurrentAppointmentAndUsesHistoricalStage() {
        Applicant applicant = applicant(ApplicantStatus.SCHEDULED);
        Booking booking = booking(11L, applicant, InterviewStage.FINAL, BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 21, 9, 0));
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L)).thenReturn(List.of(booking));

        var state = service.load(42L).currentState();

        assertEquals(InterviewStage.FINAL, state.currentInterview().interviewStage());
        assertEquals(ApplicantNextAction.RECORD_ATTENDANCE, state.nextAction());
    }

    @Test
    void passedAndOfferedActionsUseHiringReadPortResults() {
        Applicant passed = applicant(ApplicantStatus.PASSED);
        whenAdmin(passed);
        when(hiringReader.read(42L)).thenReturn(new ApplicantHiringJourneyContribution(true, false, List.of()));
        assertEquals(ApplicantNextAction.ISSUE_JOB_OFFER, service.load(42L).currentState().nextAction());

        passed.setStatus(ApplicantStatus.OFFERED);
        when(hiringReader.read(42L)).thenReturn(new ApplicantHiringJourneyContribution(false, true, List.of()));
        assertEquals(ApplicantNextAction.RECORD_OFFER_DECISION, service.load(42L).currentState().nextAction());
    }

    @Test
    void inactiveAndTerminalApplicantsExposeNoMutationActions() {
        Applicant applicant = applicant(ApplicantStatus.FOR_CLIENT_INTERVIEW);
        applicant.setActive(false);
        whenAdmin(applicant);
        assertTrue(service.load(42L).actions().isEmpty());

        applicant.setActive(true);
        applicant.setStatus(ApplicantStatus.HIRED);
        var hired = service.load(42L);
        assertTrue(hired.currentState().complete());
        assertTrue(hired.actions().isEmpty());
    }

    @Test
    void clientOnHoldAndFailedStatesRemainConservative() {
        Applicant applicant = applicant(ApplicantStatus.FOR_CLIENT_INTERVIEW);
        whenAdmin(applicant);
        assertEquals(InterviewStage.CLIENT, service.load(42L).currentState().nextRequiredStage());

        applicant.setStatus(ApplicantStatus.ON_HOLD);
        assertEquals(ApplicantNextAction.REVIEW, service.load(42L).currentState().nextAction());
        assertTrue(service.load(42L).actions().isEmpty());

        applicant.setStatus(ApplicantStatus.FAILED);
        assertTrue(service.load(42L).currentState().closed());
        assertEquals(ApplicantNextAction.RECRUITMENT_CLOSED, service.load(42L).currentState().nextAction());
    }

    @Test
    void attendedUnevaluatedInterviewOffersEvaluationAction() {
        Applicant applicant = applicant(ApplicantStatus.INTERVIEWED);
        Booking attended = booking(15L, applicant, InterviewStage.FINAL, BookingStatus.ATTENDED,
                LocalDateTime.of(2026, 8, 22, 9, 0));
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L)).thenReturn(List.of(attended));

        var profile = service.load(42L);

        assertEquals(ApplicantNextAction.EVALUATE_INTERVIEW, profile.currentState().nextAction());
        assertEquals(15L, profile.actions().getFirst().bookingId());
    }

    @Test
    void timelineUsesReliableSourcesHistoricalStagesAndDeterministicEqualTimeOrder() {
        Applicant applicant = applicant(ApplicantStatus.PASSED);
        LocalDateTime sameTime = applicant.getCreatedAt();
        Booking initial = booking(20L, applicant, InterviewStage.INITIAL, BookingStatus.PASSED, sameTime);
        Booking finalBooking = booking(21L, applicant, InterviewStage.FINAL, BookingStatus.PASSED, sameTime.plusDays(1));
        Booking clientBooking = booking(22L, applicant, InterviewStage.CLIENT, BookingStatus.PASSED, sameTime.plusDays(2));
        InterviewEvaluation evaluation = evaluation(30L, applicant, clientBooking, sameTime.plusDays(2));
        InterviewEvaluation finalEvaluation = evaluation(29L, applicant, finalBooking, sameTime.plusDays(1));
        BookingRescheduleHistory reschedule = mock(BookingRescheduleHistory.class);
        when(reschedule.getId()).thenReturn(25L);
        when(reschedule.getBooking()).thenReturn(finalBooking);
        when(reschedule.getRescheduledAt()).thenReturn(sameTime.plusDays(1));
        when(reschedule.getReason()).thenReturn("Interviewer unavailable");
        ApplicantHiringJourneyEvent offered = new ApplicantHiringJourneyEvent(
                40L, ApplicantHiringEventType.JOB_OFFERED, sameTime.plusDays(3), "Admin", "Offer"
        );
        ApplicantHiringJourneyEvent hired = new ApplicantHiringJourneyEvent(
                41L, ApplicantHiringEventType.HIRED, sameTime.plusDays(4), "Admin", "Accepted"
        );
        ApplicantHiringJourneyEvent declined = new ApplicantHiringJourneyEvent(
                42L, ApplicantHiringEventType.OFFER_DECLINED, sameTime.plusDays(5), "Admin", "Declined"
        );
        ApplicantHiringJourneyEvent withdrawn = new ApplicantHiringJourneyEvent(
                43L, ApplicantHiringEventType.WITHDRAWN, sameTime.plusDays(6), "Admin", "Withdrawn"
        );
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L))
                .thenReturn(List.of(initial, finalBooking, clientBooking));
        when(rescheduleRepository.findByBookingApplicantIdOrderByRescheduledAtAscIdAsc(42L))
                .thenReturn(List.of(reschedule));
        when(evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(42L))
                .thenReturn(List.of(finalEvaluation, evaluation));
        when(hiringReader.read(42L)).thenReturn(new ApplicantHiringJourneyContribution(
                false, false, List.of(offered, hired, declined, withdrawn)
        ));

        var timeline = service.load(42L).timeline();

        assertEquals(List.of(
                RecruitmentTimelineEvent.APPLICATION_CREATED,
                RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                RecruitmentTimelineEvent.INTERVIEW_RESCHEDULED,
                RecruitmentTimelineEvent.INTERVIEW_EVALUATED,
                RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                RecruitmentTimelineEvent.INTERVIEW_EVALUATED,
                RecruitmentTimelineEvent.JOB_OFFERED,
                RecruitmentTimelineEvent.HIRED,
                RecruitmentTimelineEvent.OFFER_DECLINED,
                RecruitmentTimelineEvent.WITHDRAWN
        ), timeline.stream().map(item -> item.event()).toList());
        assertEquals(List.of(InterviewStage.FINAL, InterviewStage.CLIENT),
                timeline.stream().filter(item -> item.event() == RecruitmentTimelineEvent.INTERVIEW_EVALUATED)
                        .map(item -> item.interviewStage()).toList());
        assertFalse(timeline.stream().anyMatch(item -> item.event().name().contains("CONFIRM")
                || item.event().name().contains("CANCEL") || item.event().name().contains("ATTEND")
                || item.event().name().contains("NO_SHOW")));
    }

    @Test
    void bookedTimelineEventDoesNotClaimMutableCurrentScheduleWhileRescheduleRetainsSlotChange() {
        Applicant applicant = applicant(ApplicantStatus.SCHEDULED);
        LocalDateTime bookedAt = LocalDateTime.of(2026, 8, 20, 9, 0);
        Booking booking = booking(50L, applicant, InterviewStage.FINAL, BookingStatus.RESCHEDULED, bookedAt);
        Schedule destination = schedule(151L, LocalDate.of(2026, 8, 27), LocalTime.of(14, 0));
        booking.setSchedule(destination);

        BookingRescheduleHistory reschedule = mock(BookingRescheduleHistory.class);
        when(reschedule.getId()).thenReturn(51L);
        when(reschedule.getBooking()).thenReturn(booking);
        when(reschedule.getSnapshotVersion()).thenReturn(null);
        when(reschedule.getRescheduledAt()).thenReturn(LocalDateTime.of(2026, 8, 22, 11, 0));
        when(reschedule.getReason()).thenReturn("Interviewer unavailable");
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L)).thenReturn(List.of(booking));
        when(rescheduleRepository.findByBookingApplicantIdOrderByRescheduledAtAscIdAsc(42L))
                .thenReturn(List.of(reschedule));

        var timeline = service.load(42L).timeline();

        var booked = timeline.stream()
                .filter(item -> item.event() == RecruitmentTimelineEvent.INTERVIEW_BOOKED)
                .findFirst().orElseThrow();
        assertEquals(bookedAt, booked.occurredAt());
        assertEquals("FINAL interview booked", booked.title());
        assertEquals("Reference: BK-50", booked.description());
        assertFalse(booked.description().contains("Aug 27, 2026 2:00 PM"));

        var rescheduled = timeline.stream()
                .filter(item -> item.event() == RecruitmentTimelineEvent.INTERVIEW_RESCHEDULED)
                .findFirst().orElseThrow();
        assertTrue(rescheduled.description().contains("Appointment details unavailable for legacy reschedule"));
        assertFalse(rescheduled.description().contains("Aug 25, 2026 10:00 AM"));
        assertFalse(rescheduled.description().contains("Aug 27, 2026 2:00 PM"));
        assertTrue(rescheduled.description().contains("Reason: Interviewer unavailable"));
    }

    @Test
    void lifecycleHistorySuppliesCompleteTimelineFromImmutableAppointmentSnapshots() {
        Applicant applicant = applicant(ApplicantStatus.FAILED);
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 20, 8, 0);
        Booking booking = booking(60L, applicant, InterviewStage.INITIAL, BookingStatus.CANCELLED, createdAt);
        Schedule schedule = booking.getSchedule();
        schedule.setScheduleDate(LocalDate.of(2026, 9, 5));
        schedule.setStartTime(LocalTime.of(14, 0));
        schedule.setEndTime(LocalTime.of(15, 0));
        User operationsUser = actor(Role.ADMIN, applicant.getBranch());
        List<BookingLifecycleHistory> history = List.of(
                BookingLifecycleHistory.record(booking, schedule, operationsUser,
                        BookingLifecycleAction.BOOKING_CREATED, null, BookingStatus.BOOKED,
                        createdAt.plusMinutes(1)),
                BookingLifecycleHistory.record(booking, schedule, operationsUser,
                        BookingLifecycleAction.BOOKING_CONFIRMED, BookingStatus.BOOKED,
                        BookingStatus.CONFIRMED, createdAt.plusMinutes(2)),
                BookingLifecycleHistory.record(booking, schedule, operationsUser,
                        BookingLifecycleAction.ATTENDANCE_RECORDED, BookingStatus.CONFIRMED,
                        BookingStatus.ATTENDED, createdAt.plusMinutes(3)),
                BookingLifecycleHistory.record(booking, schedule, operationsUser,
                        BookingLifecycleAction.NO_SHOW_RECORDED, BookingStatus.CONFIRMED,
                        BookingStatus.NO_SHOW, createdAt.plusMinutes(4)),
                BookingLifecycleHistory.record(booking, schedule, operationsUser,
                        BookingLifecycleAction.BOOKING_CANCELLED, BookingStatus.CONFIRMED,
                        BookingStatus.CANCELLED, createdAt.plusMinutes(5))
        );
        schedule.setScheduleDate(LocalDate.of(2027, 1, 1));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.getRecruiter().setFullName("Changed Recruiter");
        whenAdmin(applicant);
        when(bookingRepository.findByApplicantIdOrderByBookedDateTimeAscIdAsc(42L)).thenReturn(List.of(booking));
        when(lifecycleRepository.findByBookingApplicantIdOrderByOccurredAtAscIdAsc(42L)).thenReturn(history);

        var timeline = service.load(42L).timeline();

        assertEquals(List.of(
                RecruitmentTimelineEvent.APPLICATION_CREATED,
                RecruitmentTimelineEvent.INTERVIEW_BOOKED,
                RecruitmentTimelineEvent.INTERVIEW_CONFIRMED,
                RecruitmentTimelineEvent.INTERVIEW_ATTENDED,
                RecruitmentTimelineEvent.INTERVIEW_NO_SHOW,
                RecruitmentTimelineEvent.INTERVIEW_CANCELLED
        ), timeline.stream().map(item -> item.event()).toList());
        assertEquals(1, timeline.stream()
                .filter(item -> item.event() == RecruitmentTimelineEvent.INTERVIEW_BOOKED)
                .count());
        String bookedDescription = timeline.get(1).description();
        assertTrue(bookedDescription.contains("Sep 5, 2026 2:00 PM"));
        assertTrue(bookedDescription.contains("Recruiter: Maria Santos"));
        assertFalse(bookedDescription.contains("Jan 1, 2027"));
        assertFalse(bookedDescription.contains("Changed Recruiter"));
    }

    private void whenAdmin(Applicant applicant) {
        when(securityService.requireOperationsUser()).thenReturn(actor(Role.ADMIN, applicant.getBranch()));
        when(applicantRepository.findDetailedById(42L)).thenReturn(Optional.of(applicant));
    }

    private Applicant applicant(ApplicantStatus status) {
        Branch branch = branch();
        Client client = new Client();
        client.setCompanyName("Client");
        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        position.setClient(client);
        position.setWorkLocation("Singapore");
        Applicant applicant = new Applicant();
        applicant.setId(42L);
        applicant.setCreatedAt(LocalDateTime.of(2026, 8, 1, 8, 0));
        applicant.setBranch(branch);
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setEmail("alex@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setPositionOpening(position);
        applicant.setStatus(status);
        applicant.setActive(true);
        return applicant;
    }

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(7L);
        branch.setBranchName("Manila");
        return branch;
    }

    private User actor(Role role, Branch branch) {
        User user = new User();
        user.setId(5L);
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return user;
    }

    private Booking booking(Long id, Applicant applicant, InterviewStage stage, BookingStatus status, LocalDateTime at) {
        Booking booking = Booking.forInterviewStage(stage);
        booking.setId(id);
        booking.setApplicant(applicant);
        booking.setBookingReference("BK-" + id);
        booking.setStatus(status);
        booking.setBookedDateTime(at);
        Schedule schedule = new Schedule();
        schedule.setId(id + 100);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 5));
        schedule.setStartTime(LocalTime.of(14, 0));
        schedule.setEndTime(LocalTime.of(15, 0));
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        User recruiter = new User();
        recruiter.setFullName("Maria Santos");
        schedule.setRecruiter(recruiter);
        booking.setSchedule(schedule);
        return booking;
    }

    private Schedule schedule(Long id, LocalDate date, LocalTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setScheduleDate(date);
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusHours(1));
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return schedule;
    }

    private InterviewEvaluation evaluation(
            Long id, Applicant applicant, Booking booking, LocalDateTime at
    ) {
        User evaluator = new User();
        evaluator.setFullName("Maria Santos");
        InterviewEvaluation evaluation = InterviewEvaluation.record(
                booking, applicant, evaluator, 8, 9, 8, InterviewResult.PASS, null, at
        );
        evaluation.setId(id);
        return evaluation;
    }
}
