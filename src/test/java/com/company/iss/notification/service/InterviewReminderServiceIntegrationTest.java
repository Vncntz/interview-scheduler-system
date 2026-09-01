package com.company.iss.notification.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.notification.config.NotificationRuntimeProperties;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        InterviewReminderService.class,
        InterviewReminderClaimService.class,
        InterviewReminderCompletionService.class,
        InterviewReminderTiming.class,
        NotificationRuntimeProperties.class,
        SmtpConfigurationValidator.class,
        InterviewReminderServiceIntegrationTest.ClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterviewReminderServiceIntegrationTest {

    @Autowired InterviewReminderService reminderService;
    @Autowired InterviewReminderClaimService claimService;
    @Autowired InterviewReminderCompletionService completionService;
    @Autowired InterviewReminderDeliveryRepository deliveryRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired NotificationRuntimeProperties properties;
    @Autowired MutableClock clock;

    @MockitoBean NotificationService notificationService;

    @BeforeEach
    void enableReminders() {
        clock.setInstant(Instant.parse("2026-09-01T00:00:00Z"));
        properties.getReminders().setEnabled(true);
        properties.getReminders().setMaxAttempts(3);
        properties.getReminders().setRetryDelay(Duration.ofMinutes(10));
        when(notificationService.sendInterviewReminder(any())).thenReturn(ReminderNotificationResult.sent());
    }

    @AfterEach
    void cleanDatabase() {
        deliveryRepository.deleteAll();
        bookingRepository.deleteAll();
        applicantRepository.deleteAll();
        scheduleRepository.deleteAll();
        userRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    void sendsTwentyFourHourReminderOnceAndTwoHourReminderIndependently() {
        Booking booking = booking("INDEPENDENT", LocalDateTime.of(2026, 9, 2, 8, 0));

        InterviewReminderService.ProcessingSummary first = reminderService.processDueReminders();
        InterviewReminderService.ProcessingSummary duplicateScan = reminderService.processDueReminders();
        clock.setInstant(Instant.parse("2026-09-01T22:00:00Z"));
        InterviewReminderService.ProcessingSummary twoHour = reminderService.processDueReminders();

        assertEquals(1, first.sent());
        assertEquals(0, duplicateScan.candidates());
        assertEquals(1, twoHour.sent());
        assertEquals(2, deliveryRepository.count());
        assertEquals(1, deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                booking.getId(), 0, InterviewReminderType.REMINDER_24H
        ).orElseThrow().getAttemptCount());
        assertEquals(InterviewReminderDeliveryStatus.SENT, deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                booking.getId(), 0, InterviewReminderType.REMINDER_2H
        ).orElseThrow().getStatus());
    }

    @Test
    void staleBookingStatusIsRecheckedBeforeSending() {
        Booking booking = booking("STALE-STATUS", LocalDateTime.of(2026, 9, 1, 12, 0));
        Long bookingId = booking.getId();
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.saveAndFlush(booking);

        InterviewReminderClaimResult claim = claimService.claimNew(
                bookingId, InterviewReminderType.REMINDER_24H
        );

        assertEquals(InterviewReminderClaimResult.Disposition.SKIPPED, claim.disposition());
        assertEquals(InterviewReminderDeliveryStatus.SKIPPED, deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                bookingId, 0, InterviewReminderType.REMINDER_24H
        ).orElseThrow().getStatus());
    }

    @Test
    void oneFailureDoesNotStopOtherBookingAndRetryIsBounded() {
        Booking failedBooking = booking("RETRY-FAILED", LocalDateTime.of(2026, 9, 1, 12, 0));
        booking("RETRY-SENT", LocalDateTime.of(2026, 9, 1, 13, 0));
        when(notificationService.sendInterviewReminder(any())).thenAnswer(invocation -> {
            var context = (com.company.iss.notification.dto.InterviewReminderContext) invocation.getArgument(0);
            return context.bookingId().equals(failedBooking.getId())
                    ? ReminderNotificationResult.retryable("SMTP_CONNECTION_FAILED")
                    : ReminderNotificationResult.sent();
        });

        InterviewReminderService.ProcessingSummary first = reminderService.processDueReminders();
        clock.setInstant(Instant.parse("2026-09-01T00:10:00Z"));
        InterviewReminderService.ProcessingSummary second = reminderService.processDueReminders();
        clock.setInstant(Instant.parse("2026-09-01T00:20:00Z"));
        InterviewReminderService.ProcessingSummary third = reminderService.processDueReminders();

        var failed = deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                failedBooking.getId(), 0, InterviewReminderType.REMINDER_24H
        ).orElseThrow();
        assertEquals(1, first.sent());
        assertEquals(1, first.failed());
        assertEquals(1, second.failed());
        assertEquals(1, third.failed());
        assertEquals(3, failed.getAttemptCount());
        assertEquals(InterviewReminderDeliveryStatus.FAILED, failed.getStatus());
        assertNull(failed.getNextAttemptAt());
        assertEquals("MAX_ATTEMPTS_REACHED", failed.getStatusReason());
    }

    @Test
    void invalidRecipientIsSkippedWithoutStoppingScan() {
        Booking invalid = booking("INVALID-EMAIL", LocalDateTime.of(2026, 9, 1, 12, 0));
        invalid.getApplicant().setEmail("not-an-email");
        applicantRepository.saveAndFlush(invalid.getApplicant());
        booking("VALID-EMAIL", LocalDateTime.of(2026, 9, 1, 13, 0));

        InterviewReminderService.ProcessingSummary result = reminderService.processDueReminders();

        assertEquals(1, result.sent());
        assertEquals(1, result.skipped());
        assertEquals("INVALID_RECIPIENT", deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                invalid.getId(), 0, InterviewReminderType.REMINDER_24H
        ).orElseThrow().getStatusReason());
    }

    @Test
    void staleLeaseTokenCannotCompleteAReclaimedDelivery() {
        Booking booking = booking("STALE-TOKEN", LocalDateTime.of(2026, 9, 1, 12, 0));
        InterviewReminderClaimResult first = claimService.claimNew(
                booking.getId(), InterviewReminderType.REMINDER_24H
        );
        clock.setInstant(Instant.parse("2026-09-01T00:11:00Z"));
        InterviewReminderClaimResult reclaimed = claimService.claimRetry(
                booking.getId(), first.context().deliveryId(), InterviewReminderType.REMINDER_24H
        );

        assertEquals(false, completionService.complete(first.context(), ReminderNotificationResult.sent()));
        assertEquals(true, completionService.complete(reclaimed.context(), ReminderNotificationResult.sent()));
        var delivery = deliveryRepository.findByBookingIdAndReminderGenerationAndReminderType(
                booking.getId(), 0, InterviewReminderType.REMINDER_24H
        ).orElseThrow();
        assertEquals(2, delivery.getAttemptCount());
        assertEquals(InterviewReminderDeliveryStatus.SENT, delivery.getStatus());
    }

    @Test
    void rescheduleGenerationMakesOldClaimTerminalAndAllowsNewIdentity() {
        Booking booking = booking("NEW-GENERATION", LocalDateTime.of(2026, 9, 1, 12, 0));
        InterviewReminderClaimResult oldClaim = claimService.claimNew(
                booking.getId(), InterviewReminderType.REMINDER_24H
        );
        booking = bookingRepository.findById(booking.getId()).orElseThrow();
        booking.advanceReminderGeneration();
        bookingRepository.saveAndFlush(booking);
        clock.setInstant(Instant.parse("2026-09-01T00:11:00Z"));

        InterviewReminderClaimResult stale = claimService.claimRetry(
                booking.getId(), oldClaim.context().deliveryId(), InterviewReminderType.REMINDER_24H
        );
        InterviewReminderClaimResult current = claimService.claimNew(
                booking.getId(), InterviewReminderType.REMINDER_24H
        );

        assertEquals(InterviewReminderClaimResult.Disposition.SKIPPED, stale.disposition());
        assertEquals(InterviewReminderClaimResult.Disposition.CLAIMED, current.disposition());
        assertEquals(2, deliveryRepository.count());
        assertEquals("STALE_GENERATION", deliveryRepository.findById(oldClaim.context().deliveryId())
                .orElseThrow().getStatusReason());
    }

    @Test
    void concurrentClaimsCreateOnlyOneDeliveryIdentity() throws Exception {
        Booking booking = booking("CONCURRENT-CLAIM", LocalDateTime.of(2026, 9, 1, 12, 0));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start, booking.getId()));
            var second = executor.submit(() -> claimAfter(start, booking.getId()));
            start.countDown();

            List<InterviewReminderClaimResult.Disposition> dispositions = List.of(
                    first.get(5, TimeUnit.SECONDS).disposition(),
                    second.get(5, TimeUnit.SECONDS).disposition()
            );
            assertEquals(1, dispositions.stream()
                    .filter(value -> value == InterviewReminderClaimResult.Disposition.CLAIMED).count());
            assertEquals(1, dispositions.stream()
                    .filter(value -> value == InterviewReminderClaimResult.Disposition.DUPLICATE).count());
        }
        assertEquals(1, deliveryRepository.count());
    }

    private InterviewReminderClaimResult claimAfter(CountDownLatch start, Long bookingId) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return claimService.claimNew(bookingId, InterviewReminderType.REMINDER_24H);
    }

    private Booking booking(String suffix, LocalDateTime start) {
        Branch branch = new Branch();
        branch.setBranchCode(suffix);
        branch.setBranchName(suffix + " Branch");
        branch.setAddress("Address");
        branch.setCity("Manila");
        branch.setProvince("Metro Manila");
        branch.setActive(true);
        branch = branchRepository.saveAndFlush(branch);

        User recruiter = new User();
        recruiter.setEmail(suffix.toLowerCase() + "-recruiter@example.test");
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("Reminder Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        recruiter = userRepository.saveAndFlush(recruiter);

        Applicant applicant = new Applicant();
        applicant.setBranch(branch);
        applicant.setFirstName(suffix);
        applicant.setLastName("Applicant");
        applicant.setEmail(suffix.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        applicant = applicantRepository.saveAndFlush(applicant);

        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(start.toLocalDate());
        schedule.setStartTime(start.toLocalTime());
        schedule.setEndTime(start.toLocalTime().plusHours(1));
        schedule.setSlotCapacity(1);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.FULL);
        schedule.setActive(true);
        schedule = scheduleRepository.saveAndFlush(schedule);

        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.of(2026, 8, 30, 12, 0));
        return bookingRepository.saveAndFlush(booking);
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock reminderTestClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
