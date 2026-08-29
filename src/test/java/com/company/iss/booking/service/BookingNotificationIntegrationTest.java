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
import com.company.iss.notification.service.BookingConfirmedNotificationListener;
import com.company.iss.notification.service.BookingCreatedNotificationListener;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        BookingService.class,
        BookingCreatedNotificationListener.class,
        BookingConfirmedNotificationListener.class,
        AsyncConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingNotificationIntegrationTest {

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
    void setUpActorAndApplicantUpdates() {
        admin = saveUser("booking-notification-admin@example.test", Role.ADMIN, null);
        when(securityService.getCurrentUser()).thenReturn(admin);
        when(securityService.requireOperationsUser(any(String.class))).thenReturn(admin);
        when(applicantService.findForBookingUpdate(any(Long.class), eq(admin)))
                .thenAnswer(invocation -> applicantRepository.findById(invocation.getArgument(0)).orElseThrow());
        doAnswer(invocation -> {
            Applicant applicant = invocation.getArgument(0);
            applicant.setStatus(invocation.getArgument(1));
            return null;
        }).when(applicantService).updateStatus(any(Applicant.class), any(ApplicantStatus.class));
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
    void creationNotificationWaitsForOuterCommit() throws Exception {
        CreationFixture fixture = createCreationFixture("CREATE-COMMIT");
        CountDownLatch notificationLatch = notificationLatch(NotificationEvent.BOOKING_CREATED);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Long bookingId = transaction.execute(status -> {
            Booking booking = bookingService.createBooking(fixture.applicantId(), fixture.scheduleId(), "Created");
            verifyNoInteractions(notificationService);
            return booking.getId();
        });

        assertTrue(notificationLatch.await(5, TimeUnit.SECONDS));
        assertEquals(BookingStatus.BOOKED, bookingRepository.findById(bookingId).orElseThrow().getStatus());
        verify(notificationService).send(eq(NotificationEvent.BOOKING_CREATED), any(Booking.class));
    }

    @Test
    void confirmationRollbackDiscardsNotificationAndRestoresState() {
        Long bookingId = createBookedFixture("CONFIRM-ROLLBACK");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            bookingService.confirm(bookingId);
            verifyNoInteractions(notificationService);
            status.setRollbackOnly();
        });

        assertEquals(BookingStatus.BOOKED, bookingRepository.findById(bookingId).orElseThrow().getStatus());
        verify(notificationService, after(500).never())
                .send(eq(NotificationEvent.BOOKING_CONFIRMED), any(Booking.class));
    }

    @Test
    void notificationFailureDoesNotRollBackConfirmedBooking() throws Exception {
        Long bookingId = createBookedFixture("CONFIRM-FAILURE");
        CountDownLatch attemptedDelivery = new CountDownLatch(1);
        doAnswer(invocation -> {
            attemptedDelivery.countDown();
            throw new IllegalStateException("provider unavailable");
        }).when(notificationService).send(eq(NotificationEvent.BOOKING_CONFIRMED), any(Booking.class));

        bookingService.confirm(bookingId);

        assertTrue(attemptedDelivery.await(5, TimeUnit.SECONDS));
        assertEquals(BookingStatus.CONFIRMED, bookingRepository.findById(bookingId).orElseThrow().getStatus());
    }

    private CountDownLatch notificationLatch(NotificationEvent event) {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(notificationService).send(eq(event), any(Booking.class));
        return latch;
    }

    private CreationFixture createCreationFixture(String suffix) {
        Branch branch = saveBranch(suffix);
        User recruiter = saveUser(suffix.toLowerCase() + "-recruiter@example.test", Role.RECRUITER, branch);
        Schedule schedule = saveSchedule(branch, recruiter, 0, 2, ScheduleStatus.OPEN);
        Applicant applicant = saveApplicant("BK-" + suffix, branch, ApplicantStatus.NEW);
        return new CreationFixture(applicant.getId(), schedule.getId());
    }

    private Long createBookedFixture(String suffix) {
        Branch branch = saveBranch(suffix);
        User recruiter = saveUser(suffix.toLowerCase() + "-recruiter@example.test", Role.RECRUITER, branch);
        Schedule schedule = saveSchedule(branch, recruiter, 1, 2, ScheduleStatus.OPEN);
        Applicant applicant = saveApplicant("BK-" + suffix, branch, ApplicantStatus.SCHEDULED);
        Booking booking = new Booking();
        booking.setBookingReference("BK-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now().minusDays(1));
        return bookingRepository.saveAndFlush(booking).getId();
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

    private Applicant saveApplicant(String reference, Branch branch, ApplicantStatus status) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(reference);
        applicant.setLastName("Applicant");
        applicant.setEmail(reference.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setStatus(status);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private record CreationFixture(Long applicantId, Long scheduleId) {
    }
}
