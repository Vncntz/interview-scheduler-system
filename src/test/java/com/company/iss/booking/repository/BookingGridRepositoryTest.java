package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
import com.company.iss.client.entity.Client;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class BookingGridRepositoryTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired EntityManager entityManager;

    @Test
    void recruiterScopeUsesApplicantBranchWithStablePagingAndInitializedDisplayGraph() {
        Branch applicantBranch = persistBranch("APP", "Applicant Branch");
        Branch scheduleBranch = persistBranch("SCH", "Schedule Branch");
        User applicantBranchRecruiter = persistRecruiter("app@example.test", applicantBranch);
        User scheduleBranchRecruiter = persistRecruiter("schedule@example.test", scheduleBranch);
        PositionOpening position = persistPosition();
        LocalDate interviewDate = LocalDate.of(2026, 9, 15);
        Schedule scheduleInOtherBranch = persistSchedule(scheduleBranch, scheduleBranchRecruiter, interviewDate);
        Schedule scheduleInApplicantBranch = persistSchedule(applicantBranch, applicantBranchRecruiter, interviewDate);

        Applicant alex = persistApplicant("Alex", null, "Candidate", "alex@example.test", applicantBranch, position);
        Applicant bella = persistApplicant("Bella", null, "Candidate", "bella@example.test", applicantBranch, position);
        Applicant carlo = persistApplicant("Carlo", null, "Candidate", "carlo@example.test", applicantBranch, position);
        Applicant outOfScope = persistApplicant("Outside", null, "Candidate", "outside@example.test", scheduleBranch, position);

        Booking first = persistBooking("BK-FIRST", alex, scheduleInOtherBranch, scheduleBranchRecruiter);
        Booking second = persistBooking("BK-SECOND", bella, scheduleInOtherBranch, scheduleBranchRecruiter);
        Booking third = persistBooking("BK-THIRD", carlo, scheduleInOtherBranch, scheduleBranchRecruiter);
        persistBooking("BK-OUTSIDE", outOfScope, scheduleInApplicantBranch, applicantBranchRecruiter);
        entityManager.flush();
        entityManager.clear();

        List<Booking> pageZero = bookingRepository.findGridPage(
                applicantBranch.getId(), null, null, null, PageRequest.of(0, 2)
        );
        List<Booking> pageOne = bookingRepository.findGridPage(
                applicantBranch.getId(), null, null, null, PageRequest.of(1, 2)
        );

        assertEquals(List.of(third.getId(), second.getId()), pageZero.stream().map(Booking::getId).toList());
        assertEquals(List.of(first.getId()), pageOne.stream().map(Booking::getId).toList());
        assertEquals(3L, bookingRepository.countGrid(applicantBranch.getId(), null, null, null));

        var pageZeroIds = new HashSet<>(pageZero.stream().map(Booking::getId).toList());
        assertTrue(pageOne.stream().map(Booking::getId).noneMatch(pageZeroIds::contains));
        pageZero.forEach(booking -> {
            assertEquals(applicantBranch.getId(), booking.getApplicant().getBranch().getId());
            assertEquals(scheduleBranch.getId(), booking.getSchedule().getBranch().getId());
            assertTrue(Hibernate.isInitialized(booking.getApplicant()));
            assertTrue(Hibernate.isInitialized(booking.getApplicant().getBranch()));
            assertTrue(Hibernate.isInitialized(booking.getApplicant().getPositionOpening()));
            assertTrue(Hibernate.isInitialized(booking.getApplicant().getPositionOpening().getClient()));
            assertTrue(Hibernate.isInitialized(booking.getSchedule()));
            assertTrue(Hibernate.isInitialized(booking.getSchedule().getBranch()));
            assertTrue(Hibernate.isInitialized(booking.getSchedule().getRecruiter()));
            assertTrue(Hibernate.isInitialized(booking.getRecruiter()));
        });
    }

    @Test
    void keywordStatusAndExactScheduleDateAgreeBetweenFetchAndCount() {
        Branch branch = persistBranch("FILTER", "Filter Branch");
        User recruiter = persistRecruiter("filter@example.test", branch);
        PositionOpening position = persistPosition();
        LocalDate targetDate = LocalDate.of(2026, 10, 20);
        Schedule targetSchedule = persistSchedule(branch, recruiter, targetDate);
        Schedule otherSchedule = persistSchedule(branch, recruiter, targetDate.plusDays(1));
        Applicant targetApplicant = persistApplicant(
                "Alex", null, "Candidate", "alex.filter@example.test", branch, position
        );
        Applicant otherApplicant = persistApplicant(
                "Alex", "Marie", "Candidate", "alex.other@example.test", branch, position
        );
        Booking target = persistBooking("BK-TARGET", targetApplicant, targetSchedule, recruiter);
        persistBooking("BK-OTHER", otherApplicant, otherSchedule, recruiter);
        entityManager.flush();
        entityManager.clear();

        List<Booking> matches = bookingRepository.findGridPage(
                branch.getId(), "alex candidate", BookingStatus.CONFIRMED, targetDate, PageRequest.of(0, 50)
        );

        assertEquals(List.of(target.getId()), matches.stream().map(Booking::getId).toList());
        assertEquals(1L, bookingRepository.countGrid(
                branch.getId(), "alex candidate", BookingStatus.CONFIRMED, targetDate
        ));
        assertEquals(1L, bookingRepository.countGrid(branch.getId(), "bk-target", null, null));
        assertEquals(0L, bookingRepository.countGrid(
                branch.getId(), "alex candidate", BookingStatus.CONFIRMED, targetDate.plusDays(1)
        ));
    }

    @Test
    void interviewStageSnapshotIsPersisted() {
        Branch branch = persistBranch("STAGE", "Stage Branch");
        User recruiter = persistRecruiter("stage@example.test", branch);
        PositionOpening position = persistPosition();
        Schedule schedule = persistSchedule(branch, recruiter, LocalDate.of(2026, 11, 10));
        Applicant applicant = persistApplicant(
                "Client", null, "Candidate", "client.stage@example.test", branch, position
        );
        Booking booking = Booking.forInterviewStage(InterviewStage.CLIENT);
        booking.setBookingReference("BK-CLIENT-STAGE");
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.CONFIRMED);
        entityManager.persist(booking);
        entityManager.flush();
        entityManager.clear();

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();

        assertEquals(InterviewStage.CLIENT, reloaded.getInterviewStage());
    }

    private Branch persistBranch(String code, String name) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(name);
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        entityManager.persist(branch);
        return branch;
    }

    private User persistRecruiter(String email, Branch branch) {
        User recruiter = new User();
        recruiter.setEmail(email);
        recruiter.setPasswordHash("encoded-password");
        recruiter.setFullName("Recruiter " + branch.getBranchCode());
        recruiter.setRole(Role.RECRUITER);
        recruiter.setBranch(branch);
        recruiter.setActive(true);
        entityManager.persist(recruiter);
        return recruiter;
    }

    private PositionOpening persistPosition() {
        Client client = new Client();
        client.setCompanyName("Booking Grid Client");
        client.setAddress("Client Address");
        entityManager.persist(client);

        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer");
        position.setClient(client);
        position.setWorkLocation("Manila");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(5);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        entityManager.persist(position);
        return position;
    }

    private Applicant persistApplicant(
            String firstName,
            String middleName,
            String lastName,
            String email,
            Branch branch,
            PositionOpening position
    ) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(firstName);
        applicant.setMiddleName(middleName);
        applicant.setLastName(lastName);
        applicant.setEmail(email);
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        entityManager.persist(applicant);
        return applicant;
    }

    private Schedule persistSchedule(Branch branch, User recruiter, LocalDate date) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(date);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(10);
        schedule.setBookedCount(0);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        entityManager.persist(schedule);
        return schedule;
    }

    private Booking persistBooking(String reference, Applicant applicant, Schedule schedule, User recruiter) {
        Booking booking = new Booking();
        booking.setBookingReference(reference);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(recruiter);
        booking.setStatus(BookingStatus.CONFIRMED);
        entityManager.persist(booking);
        return booking;
    }
}
