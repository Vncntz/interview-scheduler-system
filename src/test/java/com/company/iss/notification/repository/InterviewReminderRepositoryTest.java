package com.company.iss.notification.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class InterviewReminderRepositoryTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired InterviewReminderDeliveryRepository deliveryRepository;
    @Autowired EntityManager entityManager;

    @Test
    void candidateQueryUsesExactWindowEligibilityAndIndependentReminderIdentity() {
        Fixture fixture = fixture("WINDOW");
        Booking lowerBoundary = booking(fixture, "LOWER", LocalDateTime.of(2026, 9, 1, 10, 0), BookingStatus.BOOKED);
        Booking upperBoundary = booking(fixture, "UPPER", LocalDateTime.of(2026, 9, 2, 8, 0), BookingStatus.CONFIRMED);
        booking(fixture, "OUTSIDE", LocalDateTime.of(2026, 9, 2, 8, 1), BookingStatus.BOOKED);
        booking(fixture, "CANCELLED", LocalDateTime.of(2026, 9, 1, 12, 0), BookingStatus.CANCELLED);
        entityManager.flush();

        List<Long> beforeDelivery = twentyFourHourCandidates();
        assertEquals(List.of(upperBoundary.getId()), beforeDelivery);

        InterviewReminderDelivery sent = InterviewReminderDelivery.pending(
                upperBoundary, InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 2, 8, 0)
        );
        sent.claim("11111111-1111-1111-1111-111111111111", LocalDateTime.of(2026, 9, 1, 0, 0));
        sent.markSent(LocalDateTime.of(2026, 9, 1, 0, 1));
        deliveryRepository.saveAndFlush(sent);

        assertEquals(List.of(), twentyFourHourCandidates());
        assertEquals(List.of(upperBoundary.getId()), bookingRepository.findReminderCandidateIds(
                eligibleStatuses(), InterviewReminderType.REMINDER_2H,
                LocalDate.of(2026, 9, 1), LocalTime.of(10, 0),
                LocalDate.of(2026, 9, 2), LocalTime.of(8, 0), PageRequest.of(0, 20)
        ));
        assertEquals(0, lowerBoundary.getReminderGeneration());
    }

    @Test
    void databaseUniquenessRejectsSameBookingGenerationAndType() {
        Fixture fixture = fixture("UNIQUE");
        Booking booking = booking(fixture, "BOOKING", LocalDateTime.of(2026, 9, 1, 12, 0), BookingStatus.BOOKED);
        deliveryRepository.saveAndFlush(InterviewReminderDelivery.pending(
                booking, InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 1, 12, 0)
        ));

        assertThrows(DataIntegrityViolationException.class, () -> deliveryRepository.saveAndFlush(
                InterviewReminderDelivery.pending(
                        booking, InterviewReminderType.REMINDER_24H, LocalDateTime.of(2026, 9, 1, 12, 0)
                )
        ));
    }

    @Test
    void retryQueryReturnsDueFailureAndStaleClaimButNotFreshClaim() {
        Fixture fixture = fixture("RETRY");
        Booking failedBooking = booking(
                fixture, "FAILED", LocalDateTime.of(2026, 9, 1, 12, 0), BookingStatus.BOOKED
        );
        Booking staleBooking = booking(
                fixture, "STALE", LocalDateTime.of(2026, 9, 1, 13, 0), BookingStatus.CONFIRMED
        );
        Booking freshBooking = booking(
                fixture, "FRESH", LocalDateTime.of(2026, 9, 1, 14, 0), BookingStatus.BOOKED
        );
        InterviewReminderDelivery failed = claimed(failedBooking, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        failed.markFailed(LocalDateTime.of(2026, 9, 1, 0, 0), "SMTP_CONNECTION_FAILED");
        deliveryRepository.save(failed);
        deliveryRepository.save(claimed(staleBooking, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        InterviewReminderDelivery fresh = claimed(freshBooking, "cccccccc-cccc-cccc-cccc-cccccccccccc");
        entityManager.flush();

        List<Long> ids = deliveryRepository.findRetryCandidates(
                InterviewReminderType.REMINDER_24H, eligibleStatuses(), 3,
                LocalDateTime.of(2026, 9, 1, 0, 10), LocalDateTime.of(2026, 9, 1, 0, 5),
                LocalDate.of(2026, 9, 1), LocalTime.of(10, 0),
                LocalDate.of(2026, 9, 2), LocalTime.of(8, 0), PageRequest.of(0, 20)
        ).stream().map(candidate -> candidate.bookingId()).toList();

        assertEquals(Set.of(failedBooking.getId(), staleBooking.getId()), Set.copyOf(ids));
        assertEquals(freshBooking.getId(), fresh.getBooking().getId());
    }

    private InterviewReminderDelivery claimed(Booking booking, String token) {
        InterviewReminderDelivery delivery = InterviewReminderDelivery.pending(
                booking,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(booking.getSchedule().getScheduleDate(), booking.getSchedule().getStartTime())
        );
        delivery.claim(token, LocalDateTime.of(2026, 9, 1, 0, 0));
        return delivery;
    }

    private List<Long> twentyFourHourCandidates() {
        return bookingRepository.findReminderCandidateIds(
                eligibleStatuses(), InterviewReminderType.REMINDER_24H,
                LocalDate.of(2026, 9, 1), LocalTime.of(10, 0),
                LocalDate.of(2026, 9, 2), LocalTime.of(8, 0), PageRequest.of(0, 20)
        );
    }

    private List<BookingStatus> eligibleStatuses() {
        return List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED);
    }

    private Fixture fixture(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode(suffix);
        branch.setBranchName(suffix + " Branch");
        branch.setAddress("Address");
        branch.setCity("Manila");
        branch.setProvince("Metro Manila");
        branch.setActive(true);
        entityManager.persist(branch);

        User recruiter = new User();
        recruiter.setEmail(suffix.toLowerCase() + "@example.test");
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("Reminder Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        entityManager.persist(recruiter);
        return new Fixture(branch, recruiter);
    }

    private Booking booking(
            Fixture fixture,
            String suffix,
            LocalDateTime start,
            BookingStatus status
    ) {
        Applicant applicant = new Applicant();
        applicant.setBranch(fixture.branch());
        applicant.setFirstName(suffix);
        applicant.setLastName("Applicant");
        applicant.setEmail(suffix.toLowerCase() + "-reminder@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        entityManager.persist(applicant);

        Schedule schedule = new Schedule();
        schedule.setBranch(fixture.branch());
        schedule.setRecruiter(fixture.recruiter());
        schedule.setScheduleDate(start.toLocalDate());
        schedule.setStartTime(start.toLocalTime());
        schedule.setEndTime(start.toLocalTime().plusHours(1));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        entityManager.persist(schedule);

        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(fixture.recruiter());
        booking.setStatus(status);
        booking.setBookedDateTime(LocalDateTime.of(2026, 8, 30, 12, 0));
        entityManager.persist(booking);
        return booking;
    }

    private record Fixture(Branch branch, User recruiter) {
    }
}
