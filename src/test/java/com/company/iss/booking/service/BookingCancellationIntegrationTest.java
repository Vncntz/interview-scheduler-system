package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.config.AsyncConfig;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.BookingCancelledNotificationListener;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({BookingService.class, BookingCancelledNotificationListener.class, AsyncConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingCancellationIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

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

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ApplicantService applicantService;

    @MockitoBean
    private SecurityService securityService;

    private User admin;

    @BeforeEach
    void setUpActor() {
        admin = saveUser("cancel-admin@example.test", Role.ADMIN, null);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(securityService.requireOperationsUser(any(String.class))).thenReturn(admin);
    }

    @AfterEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        applicantRepository.deleteAll();
        scheduleRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    void cancellationPersistsScheduleCapacityAndBookingStatusOnFreshReadback() throws Exception {
        CancellationFixture fixture = createFixture("READBACK", 2, 2, ScheduleStatus.FULL);
        CountDownLatch notificationLatch = notificationLatch(1);

        bookingService.cancel(fixture.bookingId());

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        assertCancelledDatabaseState(fixture, 1, ScheduleStatus.OPEN);
        Applicant applicant = applicantRepository.findById(fixture.applicantId()).orElseThrow();
        assertEquals(ApplicantStatus.SCHEDULED, applicant.getStatus());
        verify(notificationService).send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
    }

    @Test
    void repeatedCancellationDecrementsAndNotifiesExactlyOnce() throws Exception {
        CancellationFixture fixture = createFixture("REPEATED", 1, 1, ScheduleStatus.FULL);
        CountDownLatch notificationLatch = notificationLatch(1);

        bookingService.cancel(fixture.bookingId());
        bookingService.cancel(fixture.bookingId());

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        assertCancelledDatabaseState(fixture, 0, ScheduleStatus.OPEN);
        verify(notificationService, times(1))
                .send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
    }

    @Test
    void concurrentDuplicateCancellationsDecrementAndNotifyExactlyOnce() throws Exception {
        CancellationFixture fixture = createFixture("CONCURRENT", 1, 1, ScheduleStatus.FULL);
        CountDownLatch notificationLatch = notificationLatch(1);
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptCancel(startGate, fixture.bookingId()));
            Future<Boolean> second = executor.submit(() -> attemptCancel(startGate, fixture.bookingId()));

            startGate.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
        }

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        assertCancelledDatabaseState(fixture, 0, ScheduleStatus.OPEN);
        verify(notificationService, times(1))
                .send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
    }

    @Test
    void cancellationEventWaitsForOuterCommit() throws Exception {
        CancellationFixture fixture = createFixture("COMMIT", 1, 1, ScheduleStatus.FULL);
        CountDownLatch notificationLatch = notificationLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.cancel(fixture.bookingId());
            verifyNoInteractions(notificationService);
        });

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        assertCancelledDatabaseState(fixture, 0, ScheduleStatus.OPEN);
    }

    @Test
    void outerRollbackRestoresBookingAndScheduleAndDiscardsNotification() {
        CancellationFixture fixture = createFixture("ROLLBACK", 1, 1, ScheduleStatus.FULL);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            bookingService.cancel(fixture.bookingId());
            verifyNoInteractions(notificationService);
            status.setRollbackOnly();
        });

        Booking booking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
        Schedule schedule = scheduleRepository.findById(fixture.scheduleId()).orElseThrow();
        assertEquals(BookingStatus.BOOKED, booking.getStatus());
        assertEquals(1, schedule.getBookedCount());
        assertEquals(ScheduleStatus.FULL, schedule.getStatus());
        verify(notificationService, after(500).never())
                .send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
    }

    private boolean attemptCancel(CountDownLatch startGate, Long bookingId) throws InterruptedException {
        startGate.await(5, TimeUnit.SECONDS);
        try {
            bookingService.cancel(bookingId);
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
        }).when(notificationService).send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
        return latch;
    }

    private CancellationFixture createFixture(
            String suffix,
            int bookedCount,
            int capacity,
            ScheduleStatus status
    ) {
        Branch branch = saveBranch(suffix);
        User recruiter = saveUser(suffix.toLowerCase() + "-recruiter@example.test", Role.RECRUITER, branch);
        Schedule schedule = saveSchedule(branch, recruiter, bookedCount, capacity, status);
        Applicant applicant = saveApplicant("BK-" + suffix, branch);
        Booking booking = saveBooking("BK-" + suffix, applicant, schedule);
        return new CancellationFixture(booking.getId(), schedule.getId(), applicant.getId());
    }

    private void assertCancelledDatabaseState(
            CancellationFixture fixture,
            int expectedBookedCount,
            ScheduleStatus expectedScheduleStatus
    ) {
        Booking booking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
        Schedule schedule = scheduleRepository.findById(fixture.scheduleId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(expectedBookedCount, schedule.getBookedCount());
        assertEquals(expectedScheduleStatus, schedule.getStatus());
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

    private Applicant saveApplicant(String reference, Branch branch) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(reference);
        applicant.setLastName("Applicant");
        applicant.setEmail(reference.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private Booking saveBooking(String reference, Applicant applicant, Schedule schedule) {
        Booking booking = new Booking();
        booking.setBookingReference(reference);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now().minusDays(1));
        return bookingRepository.saveAndFlush(booking);
    }

    private record CancellationFixture(Long bookingId, Long scheduleId, Long applicantId) {
    }
}
