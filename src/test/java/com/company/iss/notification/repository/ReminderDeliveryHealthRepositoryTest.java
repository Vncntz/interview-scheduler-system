package com.company.iss.notification.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ReminderDeliveryHealthRepositoryTest {

    @Autowired InterviewReminderDeliveryRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void filtersStatusTypeAndFullInclusiveAppointmentDateWithCountParity() {
        Fixture fixture = fixture("FILTER");
        persistDelivery(fixture, "MATCH-START", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 0, 0), InterviewReminderDeliveryStatus.FAILED);
        persistDelivery(fixture, "MATCH-END", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 23, 59, 59), InterviewReminderDeliveryStatus.FAILED);
        persistDelivery(fixture, "NEXT-DAY", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 3, 0, 0), InterviewReminderDeliveryStatus.FAILED);
        persistDelivery(fixture, "WRONG-TYPE", InterviewReminderType.REMINDER_2H,
                LocalDateTime.of(2026, 9, 2, 12, 0), InterviewReminderDeliveryStatus.FAILED);
        persistDelivery(fixture, "WRONG-STATUS", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 12, 0), InterviewReminderDeliveryStatus.SENT);
        entityManager.flush();
        entityManager.clear();

        List<ReminderDeliveryHealthProjection> results = repository.findHealthPage(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 3, 0, 0),
                new OffsetLimitPageable(0, 50)
        );
        long count = repository.countHealth(
                InterviewReminderDeliveryStatus.FAILED,
                InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 9, 3, 0, 0)
        );

        assertEquals(List.of("MATCH-END", "MATCH-START"),
                results.stream().map(ReminderDeliveryHealthProjection::getBookingReference).toList());
        assertEquals(results.size(), count);
    }

    @Test
    void nonAlignedPaginationUsesDeterministicAppointmentThenIdDescendingOrder() {
        Fixture fixture = fixture("PAGE");
        persistDelivery(fixture, "OLDER", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 9, 0), InterviewReminderDeliveryStatus.PENDING);
        InterviewReminderDelivery firstTie = persistDelivery(fixture, "TIE-FIRST", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 10, 0), InterviewReminderDeliveryStatus.PENDING);
        InterviewReminderDelivery secondTie = persistDelivery(fixture, "TIE-SECOND", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 10, 0), InterviewReminderDeliveryStatus.PENDING);
        persistDelivery(fixture, "NEWEST", InterviewReminderType.REMINDER_24H,
                LocalDateTime.of(2026, 9, 2, 11, 0), InterviewReminderDeliveryStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        List<ReminderDeliveryHealthProjection> page = repository.findHealthPage(
                null, null, null, null, new OffsetLimitPageable(1, 2)
        );

        assertEquals(List.of(secondTie.getId(), firstTie.getId()),
                page.stream().map(ReminderDeliveryHealthProjection::getDeliveryId).toList());
        assertEquals(4L, repository.countHealth(null, null, null, null));
    }

    private InterviewReminderDelivery persistDelivery(
            Fixture fixture,
            String reference,
            InterviewReminderType type,
            LocalDateTime snapshot,
            InterviewReminderDeliveryStatus status
    ) {
        Schedule schedule = new Schedule();
        schedule.setBranch(fixture.branch());
        schedule.setRecruiter(fixture.recruiter());
        schedule.setScheduleDate(snapshot.toLocalDate());
        schedule.setStartTime(snapshot.toLocalTime());
        schedule.setEndTime(snapshot.toLocalTime().plusMinutes(30));
        schedule.setSlotCapacity(1);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        entityManager.persist(schedule);

        Applicant applicant = new Applicant();
        applicant.setBranch(fixture.branch());
        applicant.setFirstName(reference);
        applicant.setLastName("Applicant");
        applicant.setEmail(reference.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        entityManager.persist(applicant);

        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference(reference);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(fixture.recruiter());
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(snapshot.minusDays(1));
        entityManager.persist(booking);

        InterviewReminderDelivery delivery = InterviewReminderDelivery.pending(booking, type, snapshot);
        if (status == InterviewReminderDeliveryStatus.SENT) {
            delivery.claim("11111111-1111-1111-1111-111111111111", snapshot.minusHours(1));
            delivery.markSent(snapshot.minusMinutes(59));
        } else if (status == InterviewReminderDeliveryStatus.FAILED) {
            delivery.claim("22222222-2222-2222-2222-222222222222", snapshot.minusHours(1));
            delivery.markFailed(snapshot.minusMinutes(50), "SMTP_CONNECTION_FAILED");
        } else if (status == InterviewReminderDeliveryStatus.SKIPPED) {
            delivery.markSkipped("EMAIL_DISABLED");
        }
        return repository.save(delivery);
    }

    private Fixture fixture(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode("HD" + suffix);
        branch.setBranchName("Health " + suffix);
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch.setActive(true);
        entityManager.persist(branch);

        User recruiter = new User();
        recruiter.setEmail("health-" + suffix.toLowerCase() + "@example.test");
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("Health Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        entityManager.persist(recruiter);
        return new Fixture(branch, recruiter);
    }

    private record Fixture(Branch branch, User recruiter) {
    }
}
