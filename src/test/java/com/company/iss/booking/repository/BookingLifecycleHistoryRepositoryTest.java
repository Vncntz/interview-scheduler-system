package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingLifecycleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class BookingLifecycleHistoryRepositoryTest {

    @Autowired BookingLifecycleHistoryRepository historyRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired EntityManager entityManager;

    @Test
    void appendPersistsImmutableAppointmentSnapshotAndLifecycleMetadata() {
        Fixture fixture = persistFixture("snapshot");
        LocalDateTime occurredAt = LocalDateTime.of(2037, 3, 1, 8, 30);
        BookingLifecycleHistory history = record(
                fixture, BookingLifecycleAction.BOOKING_CONFIRMED,
                BookingStatus.BOOKED, BookingStatus.CONFIRMED, occurredAt
        );

        BookingLifecycleHistory appended = historyRepository.append(history);
        entityManager.flush();

        assertSame(history, appended);
        assertNotNull(appended.getId());
        assertEquals(0L, appended.getVersion());
        assertEquals(occurredAt, appended.getOccurredAt());
        assertEquals("BK-LH-snapshot", appended.getBookingReference());
        assertEquals(InterviewStage.INITIAL, appended.getInterviewStage());
        assertEquals(LocalDate.of(2037, 3, 5), appended.getAppointmentDate());
        assertEquals(LocalTime.of(9, 0), appended.getStartTime());
        assertEquals(LocalTime.of(10, 0), appended.getEndTime());
        assertEquals(InterviewMode.ONLINE, appended.getInterviewMode());
        assertEquals("Lifecycle Recruiter", appended.getRecruiterDisplayName());
        assertEquals("Lifecycle snapshot", appended.getBranchDisplayName());
    }

    @Test
    void bookingAndActionCombinationIsUnique() {
        Fixture fixture = persistFixture("unique");
        historyRepository.append(record(
                fixture, BookingLifecycleAction.BOOKING_CONFIRMED,
                BookingStatus.BOOKED, BookingStatus.CONFIRMED, LocalDateTime.now()
        ));
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            historyRepository.append(record(
                    fixture, BookingLifecycleAction.BOOKING_CONFIRMED,
                    BookingStatus.BOOKED, BookingStatus.CONFIRMED, LocalDateTime.now().plusSeconds(1)
            ));
            entityManager.flush();
        });
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void recordRejectsActionStatusCombinationsOutsideTheLifecycleContract(
            BookingLifecycleAction action,
            BookingStatus previousStatus,
            BookingStatus newStatus
    ) {
        Fixture fixture = persistFixture("invalid-" + action.ordinal() + "-" + newStatus.ordinal());

        assertThrows(IllegalArgumentException.class, () -> record(
                fixture, action, previousStatus, newStatus, LocalDateTime.now()
        ));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"BOOKED", "CONFIRMED"})
    void cancellationAcceptsBothSupportedSourceStatuses(BookingStatus previousStatus) {
        Fixture fixture = persistFixture("cancel-" + previousStatus.ordinal());

        BookingLifecycleHistory history = assertDoesNotThrow(() -> record(
                fixture,
                BookingLifecycleAction.BOOKING_CANCELLED,
                previousStatus,
                BookingStatus.CANCELLED,
                LocalDateTime.now()
        ));

        assertEquals(previousStatus, history.getPreviousStatus());
        assertEquals(BookingStatus.CANCELLED, history.getNewStatus());
    }

    @Test
    void applicantHistoryIsOrderedByTimestampThenIdentity() {
        Fixture fixture = persistFixture("ordering");
        LocalDateTime later = LocalDateTime.of(2037, 3, 1, 10, 0);
        BookingLifecycleHistory firstAtSameTime = record(
                fixture, BookingLifecycleAction.BOOKING_CONFIRMED,
                BookingStatus.BOOKED, BookingStatus.CONFIRMED, later
        );
        BookingLifecycleHistory secondAtSameTime = record(
                fixture, BookingLifecycleAction.ATTENDANCE_RECORDED,
                BookingStatus.CONFIRMED, BookingStatus.ATTENDED, later
        );
        BookingLifecycleHistory earlier = record(
                fixture, BookingLifecycleAction.BOOKING_CREATED,
                null, BookingStatus.BOOKED, later.minusMinutes(5)
        );
        historyRepository.append(firstAtSameTime);
        historyRepository.append(secondAtSameTime);
        historyRepository.append(earlier);
        entityManager.flush();
        entityManager.clear();

        List<Long> orderedIds = historyRepository
                .findByBookingApplicantIdOrderByOccurredAtAscIdAsc(fixture.applicant().getId())
                .stream()
                .map(BookingLifecycleHistory::getId)
                .toList();

        assertEquals(List.of(earlier.getId(), firstAtSameTime.getId(), secondAtSameTime.getId()), orderedIds);
    }

    @Test
    void persistedHistoryAndInheritedLifecycleStateAreIgnoredByDirtyChecking()
            throws ReflectiveOperationException {
        Fixture fixture = persistFixture("immutable");
        BookingLifecycleHistory history = record(
                fixture, BookingLifecycleAction.BOOKING_CREATED,
                null, BookingStatus.BOOKED, LocalDateTime.now().withNano(0)
        );
        historyRepository.append(history);
        entityManager.flush();
        entityManager.clear();

        BookingLifecycleHistory persisted = historyRepository
                .findByBookingIdOrderByOccurredAtAscIdAsc(fixture.booking().getId())
                .getFirst();
        LocalDateTime originalCreatedAt = persisted.getCreatedAt();
        LocalDateTime originalUpdatedAt = persisted.getUpdatedAt();
        Long originalVersion = persisted.getVersion();
        Field reference = BookingLifecycleHistory.class.getDeclaredField("bookingReference");
        reference.setAccessible(true);
        reference.set(persisted, "tampered");
        persisted.setCreatedAt(originalCreatedAt.minusDays(1));
        persisted.setUpdatedAt(originalUpdatedAt.plusDays(1));
        persisted.setVersion(originalVersion + 1);
        entityManager.flush();
        entityManager.clear();

        BookingLifecycleHistory reloaded = historyRepository
                .findByBookingIdOrderByOccurredAtAscIdAsc(fixture.booking().getId())
                .getFirst();
        assertEquals("BK-LH-immutable", reloaded.getBookingReference());
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
        assertEquals(originalUpdatedAt, reloaded.getUpdatedAt());
        assertEquals(originalVersion, reloaded.getVersion());
    }

    @Test
    void appendRejectsNullAndAssignedIdentityAndRepositoryExposesNoCrudMutationApi() {
        assertThrows(NullPointerException.class, () -> historyRepository.append(null));
        Fixture fixture = persistFixture("contract");
        BookingLifecycleHistory history = record(
                fixture, BookingLifecycleAction.BOOKING_CREATED,
                null, BookingStatus.BOOKED, LocalDateTime.now()
        );
        history.setId(99L);

        InvalidDataAccessApiUsageException exception = assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> historyRepository.append(history)
        );
        assertTrue(exception.getCause() instanceof IllegalArgumentException);

        Set<String> methodNames = Arrays.stream(BookingLifecycleHistoryRepository.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertFalse(CrudRepository.class.isAssignableFrom(BookingLifecycleHistoryRepository.class));
        assertFalse(methodNames.contains("findAll"));
        assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("save")
                || name.startsWith("delete") || name.startsWith("update")));
    }

    private Fixture persistFixture(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode("LH-" + suffix);
        branch.setBranchName("Lifecycle " + suffix);
        branch.setAddress("Test address");
        branch.setCity("Test city");
        branch.setProvince("Test province");
        branch.setActive(true);
        branch = branchRepository.save(branch);

        User recruiter = new User();
        recruiter.setEmail("lifecycle-" + suffix + "@example.test");
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("Lifecycle Recruiter");
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        recruiter = userRepository.save(recruiter);

        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.of(2037, 3, 5));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        schedule = scheduleRepository.save(schedule);

        Applicant applicant = new Applicant();
        applicant.setFirstName("Lifecycle");
        applicant.setLastName("Candidate");
        applicant.setEmail("candidate-lifecycle-" + suffix + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        applicant = applicantRepository.save(applicant);

        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-LH-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.of(2037, 3, 1, 8, 0));
        booking = bookingRepository.saveAndFlush(booking);
        return new Fixture(booking, schedule, recruiter, applicant);
    }

    private BookingLifecycleHistory record(
            Fixture fixture,
            BookingLifecycleAction action,
            BookingStatus previousStatus,
            BookingStatus newStatus,
            LocalDateTime occurredAt
    ) {
        return BookingLifecycleHistory.record(
                fixture.booking(), fixture.schedule(), fixture.actor(), action,
                previousStatus, newStatus, occurredAt
        );
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(BookingLifecycleAction.BOOKING_CREATED,
                        BookingStatus.BOOKED, BookingStatus.BOOKED),
                Arguments.of(BookingLifecycleAction.BOOKING_CREATED,
                        null, BookingStatus.CONFIRMED),
                Arguments.of(BookingLifecycleAction.BOOKING_CONFIRMED,
                        null, BookingStatus.CONFIRMED),
                Arguments.of(BookingLifecycleAction.BOOKING_CONFIRMED,
                        BookingStatus.BOOKED, BookingStatus.ATTENDED),
                Arguments.of(BookingLifecycleAction.ATTENDANCE_RECORDED,
                        BookingStatus.BOOKED, BookingStatus.ATTENDED),
                Arguments.of(BookingLifecycleAction.ATTENDANCE_RECORDED,
                        BookingStatus.CONFIRMED, BookingStatus.NO_SHOW),
                Arguments.of(BookingLifecycleAction.NO_SHOW_RECORDED,
                        BookingStatus.BOOKED, BookingStatus.NO_SHOW),
                Arguments.of(BookingLifecycleAction.NO_SHOW_RECORDED,
                        BookingStatus.CONFIRMED, BookingStatus.ATTENDED),
                Arguments.of(BookingLifecycleAction.BOOKING_CANCELLED,
                        BookingStatus.ATTENDED, BookingStatus.CANCELLED),
                Arguments.of(BookingLifecycleAction.BOOKING_CANCELLED,
                        BookingStatus.BOOKED, BookingStatus.CONFIRMED)
        );
    }

    private record Fixture(Booking booking, Schedule schedule, User actor, Applicant applicant) {
    }
}
