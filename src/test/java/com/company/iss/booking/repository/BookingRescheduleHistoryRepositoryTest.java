package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class BookingRescheduleHistoryRepositoryTest {

    @Autowired
    private BookingRescheduleHistoryRepository historyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void recordFactoryRequiresAllPersistentReferencesTimestampAndReason() {
        Booking booking = new Booking();
        Schedule source = new Schedule();
        Schedule destination = new Schedule();
        User actor = new User();
        LocalDateTime rescheduledAt = LocalDateTime.now();

        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                null, source, destination, actor, rescheduledAt, "Reason"
        ));
        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                booking, null, destination, actor, rescheduledAt, "Reason"
        ));
        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                booking, source, null, actor, rescheduledAt, "Reason"
        ));
        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                booking, source, destination, null, rescheduledAt, "Reason"
        ));
        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                booking, source, destination, actor, null, "Reason"
        ));
        assertThrows(NullPointerException.class, () -> BookingRescheduleHistory.record(
                booking, source, destination, actor, rescheduledAt, null
        ));
    }

    @Test
    void appendPersistsNewHistoryWithGeneratedIdentityAndLifecycleState() {
        Fixture fixture = persistFixture("lifecycle");
        BookingRescheduleHistory history = history(
                fixture,
                LocalDateTime.now().minusMinutes(5),
                "Candidate requested another time"
        );

        BookingRescheduleHistory appended = historyRepository.append(history);
        entityManager.flush();

        assertSame(history, appended);
        assertNotNull(appended.getId());
        assertEquals(0L, appended.getVersion());
        assertNotNull(appended.getCreatedAt());
        assertNotNull(appended.getUpdatedAt());
        assertEquals(appended.getCreatedAt(), appended.getUpdatedAt());
    }

    @Test
    void appendRejectsNullAndHistoryWithAnAssignedIdentity() {
        assertThrows(NullPointerException.class, () -> historyRepository.append(null));

        Fixture fixture = persistFixture("assigned-id");
        BookingRescheduleHistory history = history(fixture, LocalDateTime.now(), "Reason");
        history.setId(99L);

        InvalidDataAccessApiUsageException exception = assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> historyRepository.append(history)
        );

        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertEquals(
                "Persisted reschedule history cannot be appended again.",
                exception.getCause().getMessage()
        );
    }

    @Test
    void bookingHistoryIsOrderedByTimestampThenIdentity() {
        Fixture fixture = persistFixture("ordering");
        LocalDateTime later = LocalDateTime.now().withNano(0);
        BookingRescheduleHistory firstAtSameTime = history(fixture, later, "Same time first");
        BookingRescheduleHistory secondAtSameTime = history(fixture, later, "Same time second");
        BookingRescheduleHistory earlier = history(fixture, later.minusMinutes(1), "Earlier");

        historyRepository.append(firstAtSameTime);
        historyRepository.append(secondAtSameTime);
        historyRepository.append(earlier);
        entityManager.flush();
        entityManager.clear();

        List<Long> orderedIds = historyRepository
                .findByBookingIdOrderByRescheduledAtAscIdAsc(fixture.booking().getId())
                .stream()
                .map(BookingRescheduleHistory::getId)
                .toList();

        assertEquals(
                List.of(earlier.getId(), firstAtSameTime.getId(), secondAtSameTime.getId()),
                orderedIds
        );
    }

    @Test
    void persistedHistoryAndInheritedLifecycleStateAreIgnoredByDirtyChecking()
            throws ReflectiveOperationException {
        Fixture fixture = persistFixture("immutable");
        BookingRescheduleHistory history = history(fixture, LocalDateTime.now(), "Original reason");
        historyRepository.append(history);
        entityManager.flush();
        entityManager.clear();

        BookingRescheduleHistory persisted = historyRepository
                .findByBookingIdOrderByRescheduledAtAscIdAsc(fixture.booking().getId())
                .getFirst();
        LocalDateTime originalCreatedAt = persisted.getCreatedAt();
        LocalDateTime originalUpdatedAt = persisted.getUpdatedAt();
        Long originalVersion = persisted.getVersion();
        Field reason = BookingRescheduleHistory.class.getDeclaredField("reason");
        reason.setAccessible(true);
        reason.set(persisted, "Tampered reason");
        persisted.setCreatedAt(originalCreatedAt.minusDays(1));
        persisted.setUpdatedAt(originalUpdatedAt.plusDays(1));
        persisted.setVersion(originalVersion + 10);

        entityManager.flush();
        entityManager.clear();

        BookingRescheduleHistory reloaded = historyRepository
                .findByBookingIdOrderByRescheduledAtAscIdAsc(fixture.booking().getId())
                .getFirst();
        assertEquals("Original reason", reloaded.getReason());
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
        assertEquals(originalUpdatedAt, reloaded.getUpdatedAt());
        assertEquals(originalVersion, reloaded.getVersion());
    }

    @Test
    void repositoryContractExposesOnlyAppendAndBookingHistoryQuery() {
        Set<String> methodNames = Arrays.stream(BookingRescheduleHistoryRepository.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.containsAll(Set.of(
                "append",
                "findByBookingIdOrderByRescheduledAtAscIdAsc"
        )));
        assertFalse(CrudRepository.class.isAssignableFrom(BookingRescheduleHistoryRepository.class));
        assertFalse(methodNames.contains("count"));
        assertFalse(methodNames.contains("findAll"));
        assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("save")
                || name.startsWith("delete")
                || name.startsWith("update")));
    }

    private Fixture persistFixture(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode("RH-" + suffix);
        branch.setBranchName("History " + suffix);
        branch.setAddress("Test address");
        branch.setCity("Test city");
        branch.setProvince("Test province");
        branch.setActive(true);
        branch = branchRepository.save(branch);

        User recruiter = new User();
        recruiter.setEmail("history-" + suffix + "@example.test");
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("History Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        recruiter = userRepository.save(recruiter);

        Schedule source = schedule(branch, recruiter, LocalTime.of(9, 0));
        Schedule destination = schedule(branch, recruiter, LocalTime.of(11, 0));
        source = scheduleRepository.save(source);
        destination = scheduleRepository.save(destination);

        Applicant applicant = new Applicant();
        applicant.setFirstName("History");
        applicant.setLastName("Candidate");
        applicant.setEmail("candidate-" + suffix + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        applicant = applicantRepository.save(applicant);

        Booking booking = new Booking();
        booking.setBookingReference("BK-RH-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(destination);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now().minusDays(1));
        booking = bookingRepository.saveAndFlush(booking);

        return new Fixture(booking, source, destination, recruiter);
    }

    private Schedule schedule(Branch branch, User recruiter, LocalTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.now().plusDays(2));
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusHours(1));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(0);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return schedule;
    }

    private BookingRescheduleHistory history(Fixture fixture, LocalDateTime occurredAt, String reason) {
        return BookingRescheduleHistory.record(
                fixture.booking(),
                fixture.source(),
                fixture.destination(),
                fixture.actor(),
                occurredAt,
                reason
        );
    }

    private record Fixture(Booking booking, Schedule source, Schedule destination, User actor) {
    }
}
