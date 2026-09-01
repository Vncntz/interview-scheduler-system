package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.dto.BookingRescheduleCommand;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.config.AsyncConfig;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.BookingRescheduledNotificationListener;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({BookingService.class, BookingRescheduledNotificationListener.class, AsyncConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingRescheduleIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoSpyBean
    private BookingRescheduleHistoryRepository historyRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ApplicantService applicantService;

    @MockitoBean
    private SecurityService securityService;

    private User admin;

    @BeforeEach
    void setUpActor() {
        admin = saveUser("reschedule-admin@example.test", Role.ADMIN, null);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(securityService.requireOperationsUser(any(String.class))).thenReturn(admin);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from booking_reschedule_history");
        bookingRepository.deleteAll();
        applicantRepository.deleteAll();
        scheduleRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    void competingReschedulesCannotOverbookTheLastDestinationSlot() throws Exception {
        Branch branch = saveBranch("CONCURRENT");
        User recruiter = saveUser("concurrent-recruiter@example.test", Role.RECRUITER, branch);
        Schedule sourceOne = saveSchedule(branch, recruiter, 1, 2, ScheduleStatus.OPEN);
        Schedule sourceTwo = saveSchedule(branch, recruiter, 1, 2, ScheduleStatus.OPEN);
        Schedule destination = saveSchedule(branch, recruiter, 0, 1, ScheduleStatus.OPEN);
        Booking bookingOne = saveBooking("BK-CONCURRENT-1", sourceOne);
        Booking bookingTwo = saveBooking("BK-CONCURRENT-2", sourceTwo);
        CountDownLatch notificationLatch = notificationLatch(1);
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptReschedule(
                    startGate,
                    bookingOne.getId(),
                    destination.getId()
            ));
            Future<Boolean> second = executor.submit(() -> attemptReschedule(
                    startGate,
                    bookingTwo.getId(),
                    destination.getId()
            ));

            startGate.countDown();
            int successfulAttempts = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successfulAttempts);
        }

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));

        Schedule persistedDestination = scheduleRepository.findById(destination.getId()).orElseThrow();
        Booking persistedOne = bookingRepository.findById(bookingOne.getId()).orElseThrow();
        Booking persistedTwo = bookingRepository.findById(bookingTwo.getId()).orElseThrow();
        long movedBookings = List.of(persistedOne, persistedTwo).stream()
                .filter(booking -> booking.getSchedule().getId().equals(destination.getId()))
                .count();

        assertEquals(1, persistedDestination.getBookedCount());
        assertEquals(ScheduleStatus.FULL, persistedDestination.getStatus());
        assertEquals(1, movedBookings);
        assertEquals(1, historyCount());

        Schedule persistedSourceOne = scheduleRepository.findById(sourceOne.getId()).orElseThrow();
        Schedule persistedSourceTwo = scheduleRepository.findById(sourceTwo.getId()).orElseThrow();
        assertEquals(1, persistedSourceOne.getBookedCount() + persistedSourceTwo.getBookedCount());
        assertEquals(
                1,
                List.of(persistedSourceOne, persistedSourceTwo).stream()
                        .filter(source -> source.getBookedCount() == 0 && source.getStatus() == ScheduleStatus.OPEN)
                        .count()
        );
    }

    @Test
    void historyPersistenceFailureRollsBackEveryDatabaseChange() {
        RescheduleFixture fixture = createFixture("PERSISTENCE");
        doThrow(new DataIntegrityViolationException("forced history failure"))
                .when(historyRepository)
                .append(any(BookingRescheduleHistory.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> bookingService.reschedule(command(fixture))
        );

        assertOriginalDatabaseState(fixture);
        assertEquals(0, historyCount());
        verify(notificationService, after(500).never())
                .send(eq(NotificationEvent.BOOKING_RESCHEDULED), any(Booking.class));
    }

    @Test
    void notificationIsDispatchedOnlyAfterOuterTransactionCommits() throws Exception {
        RescheduleFixture fixture = createFixture("COMMIT");
        CountDownLatch notificationLatch = notificationLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.reschedule(command(fixture));
            verifyNoInteractions(notificationService);
        });

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        verify(notificationService).send(eq(NotificationEvent.BOOKING_RESCHEDULED), any(Booking.class));
        assertMovedDatabaseState(fixture);
    }

    @Test
    void registeredNotificationIsDiscardedWhenOuterTransactionRollsBack() {
        RescheduleFixture fixture = createFixture("ROLLBACK");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.reschedule(command(fixture));
            verifyNoInteractions(notificationService);
            status.setRollbackOnly();
        });

        assertOriginalDatabaseState(fixture);
        assertEquals(0, historyCount());
        verify(notificationService, after(500).never())
                .send(eq(NotificationEvent.BOOKING_RESCHEDULED), any(Booking.class));
    }

    @ParameterizedTest
    @EnumSource(InterviewStage.class)
    void reschedulingPersistsInterviewStageWithoutChangingApplicantStatus(InterviewStage interviewStage)
            throws Exception {
        RescheduleFixture fixture = createFixture("STAGE-" + interviewStage, interviewStage);
        CountDownLatch notificationLatch = notificationLatch(1);

        bookingService.reschedule(command(fixture));

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.clear();

            Booking reloadedBooking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
            Applicant reloadedApplicant = applicantRepository.findById(fixture.applicantId()).orElseThrow();
            List<BookingRescheduleHistory> history = historyRepository
                    .findByBookingIdOrderByRescheduledAtAscIdAsc(fixture.bookingId());

            assertEquals(interviewStage, reloadedBooking.getInterviewStage());
            assertEquals(fixture.applicantStatus(), reloadedApplicant.getStatus());
            assertEquals(fixture.destinationScheduleId(), reloadedBooking.getSchedule().getId());
            assertEquals(1, history.size());
            assertEquals(fixture.sourceScheduleId(), history.getFirst().getSourceSchedule().getId());
            assertEquals(fixture.destinationScheduleId(), history.getFirst().getDestinationSchedule().getId());
        });
    }

    private boolean attemptReschedule(CountDownLatch startGate, Long bookingId, Long destinationId)
            throws InterruptedException {
        startGate.await(5, TimeUnit.SECONDS);
        try {
            bookingService.reschedule(new BookingRescheduleCommand(bookingId, destinationId, "Concurrent move"));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private CountDownLatch notificationLatch(int count) {
        CountDownLatch latch = new CountDownLatch(count);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(notificationService).send(eq(NotificationEvent.BOOKING_RESCHEDULED), any(Booking.class));
        return latch;
    }

    private long historyCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from booking_reschedule_history",
                Long.class
        );
    }

    private RescheduleFixture createFixture(String suffix) {
        return createFixture(suffix, InterviewStage.INITIAL);
    }

    private RescheduleFixture createFixture(String suffix, InterviewStage interviewStage) {
        Branch branch = saveBranch(suffix);
        User recruiter = saveUser(suffix.toLowerCase() + "-recruiter@example.test", Role.RECRUITER, branch);
        Schedule source = saveSchedule(branch, recruiter, 1, 1, ScheduleStatus.FULL);
        Schedule destination = saveSchedule(branch, recruiter, 0, 1, ScheduleStatus.OPEN);
        Booking booking = saveBooking("BK-" + suffix, source, interviewStage);
        return new RescheduleFixture(
                booking.getId(),
                booking.getApplicant().getId(),
                booking.getApplicant().getStatus(),
                source.getId(),
                destination.getId()
        );
    }

    private BookingRescheduleCommand command(RescheduleFixture fixture) {
        return new BookingRescheduleCommand(
                fixture.bookingId(),
                fixture.destinationScheduleId(),
                "Integration test move"
        );
    }

    private void assertOriginalDatabaseState(RescheduleFixture fixture) {
        Booking booking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
        Schedule source = scheduleRepository.findById(fixture.sourceScheduleId()).orElseThrow();
        Schedule destination = scheduleRepository.findById(fixture.destinationScheduleId()).orElseThrow();

        assertEquals(fixture.sourceScheduleId(), booking.getSchedule().getId());
        assertEquals(0, booking.getReminderGeneration());
        assertEquals(1, source.getBookedCount());
        assertEquals(ScheduleStatus.FULL, source.getStatus());
        assertEquals(0, destination.getBookedCount());
        assertEquals(ScheduleStatus.OPEN, destination.getStatus());
    }

    private void assertMovedDatabaseState(RescheduleFixture fixture) {
        Booking booking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
        Schedule source = scheduleRepository.findById(fixture.sourceScheduleId()).orElseThrow();
        Schedule destination = scheduleRepository.findById(fixture.destinationScheduleId()).orElseThrow();

        assertEquals(fixture.destinationScheduleId(), booking.getSchedule().getId());
        assertEquals(1, booking.getReminderGeneration());
        assertEquals(0, source.getBookedCount());
        assertEquals(ScheduleStatus.OPEN, source.getStatus());
        assertEquals(1, destination.getBookedCount());
        assertEquals(ScheduleStatus.FULL, destination.getStatus());
        assertEquals(1, historyCount());
    }

    private Branch saveBranch(String code) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(code);
        branch.setAddress("Test address");
        branch.setCity("Test city");
        branch.setProvince("Test province");
        branch.setActive(true);
        return branchRepository.saveAndFlush(branch);
    }

    private User saveUser(String email, Role role, Branch branch) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName(email);
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Schedule saveSchedule(
            Branch branch,
            User recruiter,
            int bookedCount,
            int capacity,
            ScheduleStatus status
    ) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.now().plusDays(2));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(capacity);
        schedule.setBookedCount(bookedCount);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(status);
        schedule.setActive(true);
        return scheduleRepository.saveAndFlush(schedule);
    }

    private Booking saveBooking(String reference, Schedule schedule) {
        return saveBooking(reference, schedule, InterviewStage.INITIAL);
    }

    private Booking saveBooking(String reference, Schedule schedule, InterviewStage interviewStage) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(reference);
        applicant.setLastName("Applicant");
        applicant.setEmail(reference.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(schedule.getBranch());
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        applicant = applicantRepository.saveAndFlush(applicant);

        Booking booking = Booking.forInterviewStage(interviewStage);
        booking.setBookingReference(reference);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now().minusDays(1));
        return bookingRepository.saveAndFlush(booking);
    }

    private record RescheduleFixture(
            Long bookingId,
            Long applicantId,
            ApplicantStatus applicantStatus,
            Long sourceScheduleId,
            Long destinationScheduleId
    ) {
    }
}
