package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.dto.BookingRescheduleCommand;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.event.BookingCancelledEvent;
import com.company.iss.booking.event.BookingRescheduledEvent;
import com.company.iss.booking.exception.BookingCancellationException;
import com.company.iss.booking.exception.BookingRescheduleException;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRescheduleHistoryRepository historyRepository;
    @Mock
    private InterviewEvaluationRepository interviewEvaluationRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ApplicantService applicantService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                notificationService,
                bookingRepository,
                historyRepository,
                interviewEvaluationRepository,
                scheduleRepository,
                applicantService,
                securityService,
                eventPublisher
        );
    }

    @Test
    void rescheduleTransfersCapacityPreservesBookingAndWritesHistory() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        User destinationRecruiter = user(201L, Role.RECRUITER, branch);
        Schedule source = schedule(2L, branch, user(202L, Role.RECRUITER, branch), 3, 3, ScheduleStatus.FULL);
        Schedule destination = schedule(1L, branch, destinationRecruiter, 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.CONFIRMED, source);
        LocalDateTime originalBookedAt = booking.getBookedDateTime();
        String originalReference = booking.getBookingReference();

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(destination, source));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(historyRepository.save(any(BookingRescheduleHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.reschedule(new BookingRescheduleCommand(10L, 1L, "  Candidate requested a new time.  "));

        assertSame(booking, result);
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals(originalReference, result.getBookingReference());
        assertEquals(originalBookedAt, result.getBookedDateTime());
        assertSame(destination, result.getSchedule());
        assertSame(destinationRecruiter, result.getRecruiter());
        assertEquals(ApplicantStatus.SCHEDULED, result.getApplicant().getStatus());
        assertEquals(2, source.getBookedCount());
        assertEquals(ScheduleStatus.OPEN, source.getStatus());
        assertEquals(2, destination.getBookedCount());
        assertEquals(ScheduleStatus.FULL, destination.getStatus());

        ArgumentCaptor<BookingRescheduleHistory> historyCaptor =
                ArgumentCaptor.forClass(BookingRescheduleHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        BookingRescheduleHistory history = historyCaptor.getValue();
        assertSame(booking, history.getBooking());
        assertSame(source, history.getSourceSchedule());
        assertSame(destination, history.getDestinationSchedule());
        assertSame(actor, history.getActor());
        assertEquals("Candidate requested a new time.", history.getReason());
        assertNotNull(history.getRescheduledAt());
        verify(scheduleRepository).findAllByIdForUpdate(List.of(1L, 2L));
        verify(eventPublisher).publishEvent(new BookingRescheduledEvent(10L));
        verify(applicantService, never()).updateStatus(any(), any());
    }

    @Test
    void bookedReschedulePreservesBookedStatus() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Schedule destination = schedule(2L, branch, user(202L, Role.RECRUITER, branch), 0, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(historyRepository.save(any(BookingRescheduleHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "New availability"));

        assertEquals(BookingStatus.BOOKED, result.getStatus());
        assertSame(destination, result.getSchedule());
        verify(eventPublisher).publishEvent(new BookingRescheduledEvent(10L));
    }

    @Test
    void adminEligibleDestinationsAreScopedToApplicantBranch() {
        Branch applicantBranch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(
                1L, applicantBranch, user(201L, Role.RECRUITER, applicantBranch),
                1, 2, ScheduleStatus.OPEN
        );
        Schedule destination = schedule(
                2L, applicantBranch, user(202L, Role.RECRUITER, applicantBranch),
                0, 2, ScheduleStatus.OPEN
        );
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findEligibleRescheduleDestinations(
                eq(1L), any(LocalDate.class), any(LocalTime.class), eq(ScheduleStatus.OPEN), eq(100L)
        )).thenReturn(List.of(destination));

        List<Schedule> result = bookingService.findEligibleRescheduleDestinations(10L);

        assertEquals(List.of(destination), result);
        verify(scheduleRepository).findEligibleRescheduleDestinations(
                eq(1L), any(LocalDate.class), any(LocalTime.class), eq(ScheduleStatus.OPEN), eq(100L)
        );
    }

    @Test
    void recruiterEligibleDestinationsRemainScopedToApplicantBranch() {
        Branch applicantBranch = branch(100L);
        User actor = user(200L, Role.RECRUITER, applicantBranch);
        Schedule source = schedule(1L, applicantBranch, actor, 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.CONFIRMED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findEligibleRescheduleDestinations(
                eq(1L), any(LocalDate.class), any(LocalTime.class), eq(ScheduleStatus.OPEN), eq(100L)
        )).thenReturn(List.of());

        assertEquals(List.of(), bookingService.findEligibleRescheduleDestinations(10L));
        verify(scheduleRepository).findEligibleRescheduleDestinations(
                eq(1L), any(LocalDate.class), any(LocalTime.class), eq(ScheduleStatus.OPEN), eq(100L)
        );
    }

    @Test
    void eligibleDestinationsRejectBranchlessApplicantWithoutQueryingSchedules() {
        Branch sourceBranch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(
                1L, sourceBranch, user(201L, Role.RECRUITER, sourceBranch),
                1, 2, ScheduleStatus.OPEN
        );
        Booking booking = booking(10L, BookingStatus.BOOKED, source);
        booking.getApplicant().setBranch(null);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.findEligibleRescheduleDestinations(10L)
        );

        assertEquals("The applicant is not assigned to a branch.", exception.getMessage());
        verify(scheduleRepository, never()).findEligibleRescheduleDestinations(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void adminCannotCreateBookingAcrossApplicantAndScheduleBranches() {
        Branch applicantBranch = branch(100L);
        Branch scheduleBranch = branch(101L);
        User actor = user(200L, Role.ADMIN, null);
        Applicant applicant = new Applicant();
        applicant.setId(300L);
        applicant.setBranch(applicantBranch);
        applicant.setActive(true);
        Schedule schedule = schedule(
                2L, scheduleBranch, user(201L, Role.RECRUITER, scheduleBranch),
                0, 2, ScheduleStatus.OPEN
        );

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(applicantService.findForBookingUpdate(300L, actor)).thenReturn(applicant);
        when(scheduleRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(schedule));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> bookingService.createBooking(300L, 2L, "Remarks")
        );

        assertEquals(0, schedule.getBookedCount());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(applicantService, never()).updateStatus(any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void adminCannotRescheduleBookingAcrossApplicantAndDestinationBranches() {
        Branch sourceBranch = branch(100L);
        Branch destinationBranch = branch(101L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(
                1L, sourceBranch, user(201L, Role.RECRUITER, sourceBranch),
                1, 2, ScheduleStatus.OPEN
        );
        Schedule destination = schedule(
                2L, destinationBranch, user(202L, Role.RECRUITER, destinationBranch),
                0, 2, ScheduleStatus.OPEN
        );
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason"))
        );

        assertEquals("Applicant and destination schedule must belong to the same branch.", exception.getMessage());
        assertSame(source, booking.getSchedule());
        assertEquals(1, source.getBookedCount());
        assertEquals(0, destination.getBookedCount());
        verify(scheduleRepository, never()).saveAll(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameSourceAndDestinationIsRejectedWithoutWritesOrEvent() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 1L, "Reason"))
        );

        assertEquals("Select a different destination schedule.", exception.getMessage());
        verify(scheduleRepository, never()).findAllByIdForUpdate(any());
        verify(scheduleRepository, never()).saveAll(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest(name = "rejects destination when {0}")
    @MethodSource("invalidDestinations")
    void invalidDestinationIsRejectedWithoutWritesOrEvent(
            String scenario,
            boolean active,
            ScheduleStatus status,
            int bookedCount,
            int capacity,
            int dateOffsetDays,
            String expectedMessage
    ) {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Schedule destination = schedule(
                2L,
                branch,
                user(202L, Role.RECRUITER, branch),
                bookedCount,
                capacity,
                status
        );
        destination.setActive(active);
        destination.setScheduleDate(LocalDate.now().plusDays(dateOffsetDays));
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason")),
                scenario
        );

        assertEquals(expectedMessage, exception.getMessage());
        verify(scheduleRepository, never()).saveAll(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static Stream<Arguments> invalidDestinations() {
        return Stream.of(
                Arguments.of(
                        "inactive",
                        false,
                        ScheduleStatus.OPEN,
                        0,
                        2,
                        2,
                        "The destination schedule is inactive."
                ),
                Arguments.of(
                        "not open",
                        true,
                        ScheduleStatus.CLOSED,
                        0,
                        2,
                        2,
                        "The destination schedule is not open."
                ),
                Arguments.of(
                        "full",
                        true,
                        ScheduleStatus.OPEN,
                        2,
                        2,
                        2,
                        "The destination schedule is full."
                ),
                Arguments.of(
                        "elapsed",
                        true,
                        ScheduleStatus.OPEN,
                        0,
                        2,
                        -1,
                        "The destination schedule must be in the future."
                )
        );
    }

    @Test
    void rescheduleRejectsMissingReasonBeforeLocking() {
        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 20L, "  "))
        );

        assertEquals("Reschedule reason is required.", exception.getMessage());
        verify(bookingRepository, never()).findByIdForUpdate(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"BOOKED", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void rescheduleRejectsEveryNonActiveBookingStatus(BookingStatus status) {
        User actor = user(200L, Role.ADMIN, null);
        Booking booking = booking(10L, status, schedule(
                1L,
                branch(100L),
                user(201L, Role.RECRUITER, branch(100L)),
                1,
                2,
                ScheduleStatus.OPEN
        ));
        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 20L, "Reason"))
        );

        verify(scheduleRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    void recruiterCannotMoveBookingToAnotherBranch() {
        Branch sourceBranch = branch(100L);
        Branch destinationBranch = branch(101L);
        User actor = user(200L, Role.RECRUITER, sourceBranch);
        Schedule source = schedule(1L, sourceBranch, actor, 1, 2, ScheduleStatus.OPEN);
        Schedule destination = schedule(
                2L,
                destinationBranch,
                user(201L, Role.RECRUITER, destinationBranch),
                0,
                2,
                ScheduleStatus.OPEN
        );
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));

        assertThrows(
                AccessDeniedException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason"))
        );

        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void fullDestinationIsRevalidatedAfterLocksAreAcquired() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Schedule destination = schedule(2L, branch, user(202L, Role.RECRUITER, branch), 2, 2, ScheduleStatus.FULL);
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason"))
        );

        assertEquals("The destination schedule is not open.", exception.getMessage());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void evaluatedBookingIsRejectedEvenWhenItsStatusIsStillBooked() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Booking booking = booking(
                10L,
                BookingStatus.BOOKED,
                schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN)
        );
        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(interviewEvaluationRepository.existsByBookingId(10L)).thenReturn(true);

        BookingRescheduleException exception = assertThrows(
                BookingRescheduleException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason"))
        );

        assertEquals("Evaluated interviews cannot be rescheduled.", exception.getMessage());
        verify(scheduleRepository, never()).findAllByIdForUpdate(any());
    }

    @Test
    void historyFailurePreventsAfterCommitEventPublication() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule source = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Schedule destination = schedule(2L, branch, user(202L, Role.RECRUITER, branch), 0, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.BOOKED, source);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(source, destination));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(historyRepository.save(any())).thenThrow(new IllegalStateException("Database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> bookingService.reschedule(new BookingRescheduleCommand(10L, 2L, "Reason"))
        );

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelReloadsLockedStateAndPersistsScheduleAndBooking() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule detachedSchedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 2, 2, ScheduleStatus.FULL);
        Schedule lockedSchedule = schedule(1L, branch, detachedSchedule.getRecruiter(), 2, 2, ScheduleStatus.FULL);
        Booking booking = booking(10L, BookingStatus.CONFIRMED, detachedSchedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedSchedule));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancel(10L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(1, lockedSchedule.getBookedCount());
        assertEquals(ScheduleStatus.OPEN, lockedSchedule.getStatus());
        assertEquals(2, detachedSchedule.getBookedCount());
        assertEquals(ScheduleStatus.FULL, detachedSchedule.getStatus());
        assertEquals(ApplicantStatus.SCHEDULED, booking.getApplicant().getStatus());
        verify(scheduleRepository).save(lockedSchedule);
        verify(bookingRepository).save(booking);
        verify(eventPublisher).publishEvent(new BookingCancelledEvent(10L));
        verifyNoInteractions(notificationService);
    }

    @Test
    void repeatedCancelIsIdempotentWithoutScheduleLockWritesOrEvent() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule schedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 0, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.CANCELLED, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        bookingService.cancel(10L);

        verify(scheduleRepository, never()).findByIdForUpdate(any());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(notificationService);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"BOOKED", "CONFIRMED", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void cancelRejectsEveryNonCancellableStatusWithoutWritesOrEvent(BookingStatus status) {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule schedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, status, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        BookingCancellationException exception = assertThrows(
                BookingCancellationException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("Only booked or confirmed interviews can be cancelled.", exception.getMessage());
        assertEquals(status, booking.getStatus());
        assertEquals(1, schedule.getBookedCount());
        verify(scheduleRepository, never()).findByIdForUpdate(any());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"BOOKED", "CONFIRMED"})
    void cancelRejectsEvaluatedBookingWithoutWritesOrEvent(BookingStatus status) {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule schedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, status, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(interviewEvaluationRepository.existsByBookingId(10L)).thenReturn(true);

        BookingCancellationException exception = assertThrows(
                BookingCancellationException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("Evaluated interviews cannot be cancelled.", exception.getMessage());
        assertEquals(status, booking.getStatus());
        assertEquals(1, schedule.getBookedCount());
        verify(scheduleRepository, never()).findByIdForUpdate(any());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelPreservesNonFullScheduleStatusAndZeroCount() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule schedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 0, 2, ScheduleStatus.CLOSED);
        Booking booking = booking(10L, BookingStatus.BOOKED, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancel(10L);

        assertEquals(0, schedule.getBookedCount());
        assertEquals(ScheduleStatus.CLOSED, schedule.getStatus());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void cancelRejectsMissingIdBeforeAuthorizationOrLocking() {
        BookingCancellationException exception = assertThrows(
                BookingCancellationException.class,
                () -> bookingService.cancel(null)
        );

        assertEquals("Booking is required.", exception.getMessage());
        verifyNoInteractions(securityService, bookingRepository, scheduleRepository, eventPublisher);
    }

    @Test
    void cancelRejectsMissingBookingWithoutWrites() {
        when(securityService.getCurrentUser()).thenReturn(user(200L, Role.ADMIN, null));
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        BookingCancellationException exception = assertThrows(
                BookingCancellationException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("Booking was not found.", exception.getMessage());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void cancelRejectsMissingScheduleAfterBookingLock() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.ADMIN, null);
        Schedule schedule = schedule(1L, branch, user(201L, Role.RECRUITER, branch), 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.BOOKED, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        BookingCancellationException exception = assertThrows(
                BookingCancellationException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("The booking schedule was not found.", exception.getMessage());
        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void inactiveActorCannotCancel() {
        User actor = user(200L, Role.ADMIN, null);
        actor.setActive(false);
        when(securityService.getCurrentUser()).thenReturn(actor);

        assertThrows(AccessDeniedException.class, () -> bookingService.cancel(10L));

        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void applicantActorCannotCancel() {
        User actor = user(200L, Role.APPLICANT, null);
        when(securityService.getCurrentUser()).thenReturn(actor);

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("You are not authorized to cancel interviews.", exception.getMessage());
        verify(bookingRepository, never()).findByIdForUpdate(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameBranchRecruiterCanCancel() {
        Branch branch = branch(100L);
        User actor = user(200L, Role.RECRUITER, branch);
        Schedule schedule = schedule(1L, branch, actor, 1, 2, ScheduleStatus.OPEN);
        Booking booking = booking(10L, BookingStatus.BOOKED, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
        when(scheduleRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(schedule));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancel(10L);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(0, schedule.getBookedCount());
        verify(eventPublisher).publishEvent(new BookingCancelledEvent(10L));
    }

    @Test
    void recruiterCannotCancelBookingFromAnotherBranch() {
        Branch actorBranch = branch(100L);
        Branch bookingBranch = branch(101L);
        User actor = user(200L, Role.RECRUITER, actorBranch);
        Schedule schedule = schedule(
                1L,
                bookingBranch,
                user(201L, Role.RECRUITER, bookingBranch),
                1,
                2,
                ScheduleStatus.OPEN
        );
        Booking booking = booking(10L, BookingStatus.BOOKED, schedule);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> bookingService.cancel(10L)
        );

        assertEquals("You may only cancel interviews within your branch.", exception.getMessage());
        verify(scheduleRepository, never()).findByIdForUpdate(any());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private Booking booking(Long id, BookingStatus status, Schedule schedule) {
        Applicant applicant = new Applicant();
        applicant.setId(300L);
        applicant.setActive(true);
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setBranch(schedule.getBranch());

        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingReference("BK-ORIGINAL");
        booking.setBookedDateTime(LocalDateTime.now().minusDays(2));
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setStatus(status);
        return booking;
    }

    private Schedule schedule(
            Long id,
            Branch branch,
            User recruiter,
            int bookedCount,
            int capacity,
            ScheduleStatus status
    ) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.now().plusDays(2));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setBookedCount(bookedCount);
        schedule.setSlotCapacity(capacity);
        schedule.setStatus(status);
        schedule.setInterviewMode(InterviewMode.ONSITE);
        schedule.setActive(true);
        return schedule;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    private User user(Long id, Role role, Branch branch) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return user;
    }
}
