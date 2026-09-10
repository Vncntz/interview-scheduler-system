package com.company.iss.dashboard.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingLifecycleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
import com.company.iss.client.entity.Client;
import com.company.iss.dashboard.dto.FollowUpDeadlineFilter;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
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
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RecruiterFollowUpRepositoryTest {

    @Autowired RecruiterFollowUpRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void progressionCandidatesAreBranchScopedOrderedAndProjectedWithoutUnrelatedApplicants() {
        Branch branch = persistBranch("FOLLOW");
        Branch otherBranch = persistBranch("OTHER");
        User recruiter = persistRecruiter("follow@example.test", branch);
        User otherRecruiter = persistRecruiter("other@example.test", otherBranch);
        PositionOpening position = persistPosition("Engineer", "Acme Recruitment");
        Schedule schedule = persistSchedule(branch, recruiter);
        Schedule otherSchedule = persistSchedule(otherBranch, otherRecruiter);
        LocalDateTime oldest = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 8, 22, 10, 0);

        Applicant finalApplicant = persistApplicant(
                "Alex", "Marie", "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        persistProgression(finalApplicant, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, oldest);
        Applicant clientApplicant = persistApplicant(
                "Bella", null, "Candidate", branch, position, ApplicantStatus.FOR_CLIENT_INTERVIEW, true
        );
        persistProgression(clientApplicant, schedule, recruiter, InterviewResult.FOR_CLIENT_INTERVIEW, newer);
        Applicant unrelated = persistApplicant(
                "Carlo", null, "Candidate", branch, position, ApplicantStatus.PASSED, true
        );
        persistProgression(unrelated, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, oldest.minusDays(1));
        Applicant inactive = persistApplicant(
                "Dana", null, "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, false
        );
        persistProgression(inactive, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, oldest.minusDays(2));
        Applicant outOfScope = persistApplicant(
                "Erin", null, "Candidate", otherBranch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        persistProgression(outOfScope, otherSchedule, otherRecruiter, InterviewResult.FOR_FINAL_INTERVIEW, oldest.minusDays(3));
        entityManager.flush();
        entityManager.clear();

        List<FollowUpApplicantProjection> results = query(branch.getId());

        assertEquals(List.of(finalApplicant.getId(), clientApplicant.getId()),
                results.stream().map(FollowUpApplicantProjection::getApplicantId).toList());
        FollowUpApplicantProjection first = results.getFirst();
        assertEquals("Alex Marie Candidate", first.getApplicantName());
        assertEquals("Engineer", first.getPositionTitle());
        assertEquals("Acme Recruitment", first.getClientName());
        assertEquals(ApplicantStatus.FOR_FINAL_INTERVIEW, first.getApplicantStatus());
        assertEquals(oldest, first.getWaitingSince());
    }

    @Test
    void activeBookingsSuppressProgressionCandidatesIncludingLegacyRescheduledStatus() {
        Branch branch = persistBranch("ACTIVE");
        User recruiter = persistRecruiter("active@example.test", branch);
        PositionOpening position = persistPosition("Analyst", "Active Client");
        Schedule schedule = persistSchedule(branch, recruiter);

        for (BookingStatus activeStatus : List.of(
                BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED
        )) {
            Applicant applicant = persistApplicant(
                    activeStatus.name(), null, "Candidate", branch, position,
                    ApplicantStatus.FOR_FINAL_INTERVIEW, true
            );
            persistProgression(
                    applicant, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW,
                    LocalDateTime.of(2026, 8, 20, 10, 0)
            );
            persistBooking(applicant, schedule, recruiter, InterviewStage.FINAL, activeStatus,
                    LocalDateTime.of(2026, 8, 21, 10, 0));
        }
        entityManager.flush();
        entityManager.clear();

        assertEquals(List.of(), query(branch.getId()));
    }

    @Test
    void replacementsWithoutLifecycleEvidenceRemainVisibleWithUnavailableTiming() {
        Branch branch = persistBranch("REPLACE");
        User recruiter = persistRecruiter("replace@example.test", branch);
        PositionOpening position = persistPosition("Specialist", "Replacement Client");
        Schedule schedule = persistSchedule(branch, recruiter);
        LocalDateTime sharedBookingTime = LocalDateTime.of(2026, 8, 25, 9, 0);

        Applicant cancelledFinal = persistApplicant(
                "Final", null, "Replacement", branch, position, ApplicantStatus.SCHEDULED, true
        );
        persistBooking(
                cancelledFinal, schedule, recruiter, InterviewStage.FINAL,
                BookingStatus.CANCELLED, sharedBookingTime.minusDays(1)
        );
        Applicant noShowClient = persistApplicant(
                "Client", null, "Replacement", branch, position, ApplicantStatus.SCHEDULED, true
        );
        persistBooking(noShowClient, schedule, recruiter, InterviewStage.CLIENT,
                BookingStatus.NO_SHOW, sharedBookingTime.plusDays(1));
        Applicant initialReplacement = persistApplicant(
                "Initial", null, "Replacement", branch, position, ApplicantStatus.SCHEDULED, true
        );
        persistBooking(initialReplacement, schedule, recruiter, InterviewStage.INITIAL,
                BookingStatus.CANCELLED, sharedBookingTime.minusDays(2));
        Applicant stableTie = persistApplicant(
                "Stable", null, "Tie", branch, position, ApplicantStatus.SCHEDULED, true
        );
        persistBooking(stableTie, schedule, recruiter, InterviewStage.FINAL,
                BookingStatus.CANCELLED, sharedBookingTime);
        persistBooking(stableTie, schedule, recruiter, InterviewStage.CLIENT,
                BookingStatus.NO_SHOW, sharedBookingTime);
        entityManager.flush();
        entityManager.clear();

        List<FollowUpApplicantProjection> results = query(branch.getId());

        assertEquals(
                List.of(cancelledFinal.getId(), noShowClient.getId(), stableTie.getId()),
                results.stream().map(FollowUpApplicantProjection::getApplicantId).toList()
        );
        FollowUpApplicantProjection cancelled = results.stream()
                .filter(item -> item.getApplicantId().equals(cancelledFinal.getId()))
                .findFirst().orElseThrow();
        assertEquals(InterviewStage.FINAL, cancelled.getMostRecentBookingStage());
        assertEquals(BookingStatus.CANCELLED, cancelled.getMostRecentBookingStatus());
        assertNull(cancelled.getWaitingSince());
        FollowUpApplicantProjection tied = results.stream()
                .filter(item -> item.getApplicantId().equals(stableTie.getId()))
                .findFirst().orElseThrow();
        assertEquals(InterviewStage.CLIENT, tied.getMostRecentBookingStage());
        assertEquals(BookingStatus.NO_SHOW, tied.getMostRecentBookingStatus());
        assertNull(cancelled.getRelatedAppointmentDate());
        assertNull(cancelled.getRelatedAppointmentStartTime());
    }

    @Test
    void replacementUsesImmutableLifecycleTimingAndAppointmentSnapshot() {
        Branch branch = persistBranch("HISTORY");
        User recruiter = persistRecruiter("history@example.test", branch);
        PositionOpening position = persistPosition("Specialist", "History Client");
        Schedule schedule = persistSchedule(branch, recruiter);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 12));
        schedule.setStartTime(LocalTime.of(10, 30));
        Applicant applicant = persistApplicant(
                "History", null, "Candidate", branch, position, ApplicantStatus.SCHEDULED, true
        );
        Booking booking = persistBooking(
                applicant, schedule, recruiter, InterviewStage.FINAL,
                BookingStatus.CANCELLED, LocalDateTime.of(2026, 9, 1, 9, 0)
        );
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 13, 14, 15);
        entityManager.persist(BookingLifecycleHistory.record(
                booking, schedule, recruiter, BookingLifecycleAction.BOOKING_CANCELLED,
                BookingStatus.CONFIRMED, BookingStatus.CANCELLED, occurredAt
        ));
        booking.setRemarks("Mutable booking edit after cancellation");
        schedule.setScheduleDate(LocalDate.of(2026, 9, 20));
        schedule.setStartTime(LocalTime.of(16, 0));
        entityManager.flush();
        entityManager.clear();

        FollowUpApplicantProjection result = query(branch.getId(), InterviewStage.FINAL).getFirst();

        assertEquals(occurredAt, result.getWaitingSince());
        assertEquals(LocalDate.of(2026, 9, 12), result.getRelatedAppointmentDate());
        assertEquals(LocalTime.of(10, 30), result.getRelatedAppointmentStartTime());
    }

    @Test
    void progressionWithoutAttendanceSnapshotKeepsEvaluationTimingAndNoRelatedAppointment() {
        Branch branch = persistBranch("PROGMISS");
        User recruiter = persistRecruiter("progression.missing@example.test", branch);
        PositionOpening position = persistPosition("Engineer", "Legacy Progression Client");
        Schedule schedule = persistSchedule(branch, recruiter);
        Applicant applicant = persistApplicant(
                "Legacy", null, "Candidate", branch, position,
                ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 9, 14, 11, 0);
        persistProgression(applicant, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, evaluatedAt);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 30));
        entityManager.flush();
        entityManager.clear();

        FollowUpApplicantProjection result = query(branch.getId(), InterviewStage.FINAL).getFirst();

        assertEquals(evaluatedAt, result.getWaitingSince());
        assertNull(result.getRelatedAppointmentDate());
        assertNull(result.getRelatedAppointmentStartTime());
    }

    @Test
    void progressionSeparatesEvaluationTimingFromAttendanceAppointmentSnapshot() {
        Branch branch = persistBranch("PROGSNAP");
        User recruiter = persistRecruiter("progression.snapshot@example.test", branch);
        PositionOpening position = persistPosition("Engineer", "Progression Client");
        Schedule schedule = persistSchedule(branch, recruiter);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 14));
        schedule.setStartTime(LocalTime.of(8, 45));
        Applicant applicant = persistApplicant(
                "Progression", null, "Candidate", branch, position,
                ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 9, 14, 11, 0);
        Booking booking = persistBooking(
                applicant, schedule, recruiter, InterviewStage.INITIAL,
                BookingStatus.FOR_FINAL_INTERVIEW, evaluatedAt.minusHours(1)
        );
        entityManager.persist(InterviewEvaluation.record(
                booking, applicant, recruiter, 8, 8, 8,
                InterviewResult.FOR_FINAL_INTERVIEW, null, evaluatedAt
        ));
        entityManager.persist(BookingLifecycleHistory.record(
                booking, schedule, recruiter, BookingLifecycleAction.ATTENDANCE_RECORDED,
                BookingStatus.CONFIRMED, BookingStatus.ATTENDED, evaluatedAt.minusHours(2)
        ));
        schedule.setScheduleDate(LocalDate.of(2026, 9, 21));
        schedule.setStartTime(LocalTime.of(15, 0));
        entityManager.flush();
        entityManager.clear();

        FollowUpApplicantProjection result = query(branch.getId(), InterviewStage.FINAL).getFirst();

        assertEquals(evaluatedAt, result.getWaitingSince());
        assertEquals(LocalDate.of(2026, 9, 14), result.getRelatedAppointmentDate());
        assertEquals(LocalTime.of(8, 45), result.getRelatedAppointmentStartTime());
    }

    @Test
    void stageQueryPagesInWaitingPriorityAndAggregateCountsUseInclusiveBoundaries() {
        Branch branch = persistBranch("PAGE");
        User recruiter = persistRecruiter("page@example.test", branch);
        PositionOpening position = persistPosition("Engineer", "Paged Client");
        Schedule schedule = persistSchedule(branch, recruiter);
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        Applicant overdue = persistApplicant(
                "Overdue", null, "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        Applicant overdueTie = persistApplicant(
                "OverdueTie", null, "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        Applicant dueSoon = persistApplicant(
                "DueSoon", null, "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        Applicant onTrack = persistApplicant(
                "OnTrack", null, "Candidate", branch, position, ApplicantStatus.FOR_FINAL_INTERVIEW, true
        );
        Applicant unavailable = persistApplicant(
                "Unavailable", null, "Candidate", branch, position, ApplicantStatus.SCHEDULED, true
        );
        persistProgression(overdue, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, now.minusHours(72));
        persistProgression(overdueTie, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, now.minusHours(72));
        persistProgression(dueSoon, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, now.minusHours(48));
        persistProgression(onTrack, schedule, recruiter, InterviewResult.FOR_FINAL_INTERVIEW, now.minusHours(10));
        persistBooking(unavailable, schedule, recruiter, InterviewStage.FINAL,
                BookingStatus.CANCELLED, now.minusHours(5));
        entityManager.flush();
        entityManager.clear();

        List<FollowUpApplicantProjection> page = repository.findFollowUpsByStage(
                branch.getId(), InterviewStage.FINAL, ApplicantStatus.FOR_FINAL_INTERVIEW,
                ApplicantStatus.SCHEDULED, InterviewResult.FOR_FINAL_INTERVIEW,
                List.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
                List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED),
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                BookingLifecycleAction.ATTENDANCE_RECORDED,
                FollowUpDeadlineFilter.ALL.name(), now.minusHours(72), now.minusHours(48),
                new OffsetLimitPageable(1, 2)
        );
        FollowUpWorkloadProjection summary = repository.summarizeFollowUpsByStage(
                branch.getId(), InterviewStage.FINAL, ApplicantStatus.FOR_FINAL_INTERVIEW,
                ApplicantStatus.SCHEDULED, InterviewResult.FOR_FINAL_INTERVIEW,
                List.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
                List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED),
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                now.minusHours(72), now.minusHours(48)
        );

        assertEquals(List.of(overdueTie.getId(), dueSoon.getId()),
                page.stream().map(FollowUpApplicantProjection::getApplicantId).toList());
        assertEquals(5L, summary.getTotal());
        assertEquals(1L, summary.getOnTrack());
        assertEquals(1L, summary.getDueSoon());
        assertEquals(2L, summary.getOverdue());
        assertEquals(1L, summary.getTimingUnavailable());
        assertEquals(2L, count(branch.getId(), FollowUpDeadlineFilter.OVERDUE, now));
        assertEquals(1L, count(branch.getId(), FollowUpDeadlineFilter.DUE_SOON, now));
        assertEquals(1L, count(branch.getId(), FollowUpDeadlineFilter.ON_TRACK, now));
        assertEquals(1L, count(branch.getId(), FollowUpDeadlineFilter.TIMING_UNAVAILABLE, now));
        assertEquals(5L, count(branch.getId(), FollowUpDeadlineFilter.ALL, now));
    }

    private List<FollowUpApplicantProjection> query(Long branchId) {
        return Stream.concat(
                query(branchId, InterviewStage.FINAL).stream(),
                query(branchId, InterviewStage.CLIENT).stream()
        ).sorted(Comparator.comparing(FollowUpApplicantProjection::getWaitingSince,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FollowUpApplicantProjection::getApplicantId)).toList();
    }

    private List<FollowUpApplicantProjection> query(Long branchId, InterviewStage stage) {
        ApplicantStatus requiredStatus = stage == InterviewStage.FINAL
                ? ApplicantStatus.FOR_FINAL_INTERVIEW : ApplicantStatus.FOR_CLIENT_INTERVIEW;
        InterviewResult requiredResult = stage == InterviewStage.FINAL
                ? InterviewResult.FOR_FINAL_INTERVIEW : InterviewResult.FOR_CLIENT_INTERVIEW;
        return repository.findFollowUpsByStage(
                branchId,
                stage,
                requiredStatus,
                ApplicantStatus.SCHEDULED,
                requiredResult,
                List.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
                List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED),
                BookingStatus.CANCELLED,
                BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED,
                BookingLifecycleAction.NO_SHOW_RECORDED,
                BookingLifecycleAction.ATTENDANCE_RECORDED,
                FollowUpDeadlineFilter.ALL.name(),
                LocalDateTime.of(2026, 9, 7, 12, 0),
                LocalDateTime.of(2026, 9, 8, 12, 0),
                new OffsetLimitPageable(0, 100)
        );
    }

    private long count(Long branchId, FollowUpDeadlineFilter filter, LocalDateTime now) {
        return repository.countFollowUpsByStage(
                branchId, InterviewStage.FINAL, ApplicantStatus.FOR_FINAL_INTERVIEW,
                ApplicantStatus.SCHEDULED, InterviewResult.FOR_FINAL_INTERVIEW,
                List.of(BookingStatus.CANCELLED, BookingStatus.NO_SHOW),
                List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED),
                BookingStatus.CANCELLED, BookingStatus.NO_SHOW,
                BookingLifecycleAction.BOOKING_CANCELLED, BookingLifecycleAction.NO_SHOW_RECORDED,
                filter.name(), now.minusHours(72), now.minusHours(48)
        );
    }

    private Branch persistBranch(String code) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(code + " Branch");
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch.setActive(true);
        entityManager.persist(branch);
        return branch;
    }

    private User persistRecruiter(String email, Branch branch) {
        User recruiter = new User();
        recruiter.setEmail(email);
        recruiter.setPasswordHash("test-only-hash");
        recruiter.setFullName("Recruiter " + branch.getBranchCode());
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        entityManager.persist(recruiter);
        return recruiter;
    }

    private PositionOpening persistPosition(String title, String clientName) {
        Client client = new Client();
        client.setCompanyName(clientName);
        client.setAddress("Client Address");
        entityManager.persist(client);
        PositionOpening position = new PositionOpening();
        position.setTitle(title);
        position.setClient(client);
        position.setWorkLocation("Manila");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(5);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        entityManager.persist(position);
        return position;
    }

    private Schedule persistSchedule(Branch branch, User recruiter) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 10));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(20);
        schedule.setBookedCount(0);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        entityManager.persist(schedule);
        return schedule;
    }

    private Applicant persistApplicant(
            String firstName,
            String middleName,
            String lastName,
            Branch branch,
            PositionOpening position,
            ApplicantStatus status,
            boolean active
    ) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(firstName);
        applicant.setMiddleName(middleName);
        applicant.setLastName(lastName);
        applicant.setEmail(firstName.toLowerCase() + "." + lastName.toLowerCase() + "."
                + branch.getBranchCode().toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setStatus(status);
        applicant.setActive(active);
        entityManager.persist(applicant);
        return applicant;
    }

    private void persistProgression(
            Applicant applicant,
            Schedule schedule,
            User recruiter,
            InterviewResult result,
            LocalDateTime evaluatedAt
    ) {
        Booking booking = persistBooking(
                applicant, schedule, recruiter, InterviewStage.INITIAL,
                result == InterviewResult.FOR_FINAL_INTERVIEW
                        ? BookingStatus.FOR_FINAL_INTERVIEW
                        : BookingStatus.FOR_CLIENT_INTERVIEW,
                evaluatedAt.minusHours(1)
        );
        InterviewEvaluation evaluation = InterviewEvaluation.record(
                booking, applicant, recruiter, 8, 8, 8, result, null, evaluatedAt
        );
        entityManager.persist(evaluation);
    }

    private Booking persistBooking(
            Applicant applicant,
            Schedule schedule,
            User recruiter,
            InterviewStage stage,
            BookingStatus status,
            LocalDateTime bookedAt
    ) {
        Booking booking = Booking.forInterviewStage(stage);
        booking.setBookingReference("BK-" + applicant.getFirstName() + "-" + status + "-" + System.nanoTime());
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(status);
        booking.setBookedDateTime(bookedAt);
        entityManager.persist(booking);
        return booking;
    }
}
