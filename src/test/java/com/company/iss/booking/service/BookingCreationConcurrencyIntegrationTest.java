package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.applicant.service.ApplicantAssignmentGuard;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.dto.CreateBookingCommand;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({BookingService.class, ApplicantService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookingCreationConcurrencyIntegrationTest {

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookingRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired BranchRepository branchRepository;

    @MockitoBean SecurityService securityService;
    @MockitoBean ApplicantAssignmentGuard applicantAssignmentGuard;

    private User recruiter;

    @BeforeEach
    void setUpActor() {
        Branch branch = saveBranch();
        recruiter = saveRecruiter(branch);
        when(securityService.requireOperationsUser(anyString())).thenReturn(recruiter);
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
    void concurrentFollowUpSchedulingCreatesOnlyOneActiveBooking() throws Exception {
        Applicant applicant = saveApplicant(recruiter.getBranch());
        Schedule firstSchedule = saveSchedule(recruiter.getBranch(), LocalTime.of(9, 0));
        Schedule secondSchedule = saveSchedule(recruiter.getBranch(), LocalTime.of(11, 0));
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptCreate(
                    startGate, applicant.getId(), firstSchedule.getId()
            ));
            Future<Boolean> second = executor.submit(() -> attemptCreate(
                    startGate, applicant.getId(), secondSchedule.getId()
            ));

            startGate.countDown();
            int successfulAttempts = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successfulAttempts);
        }

        assertEquals(1, bookingRepository.count());
        assertEquals(ApplicantStatus.SCHEDULED,
                applicantRepository.findById(applicant.getId()).orElseThrow().getStatus());
        int allocatedCapacity = scheduleRepository.findById(firstSchedule.getId()).orElseThrow().getBookedCount()
                + scheduleRepository.findById(secondSchedule.getId()).orElseThrow().getBookedCount();
        assertEquals(1, allocatedCapacity);
    }

    private boolean attemptCreate(CountDownLatch startGate, Long applicantId, Long scheduleId)
            throws InterruptedException {
        startGate.await(5, TimeUnit.SECONDS);
        try {
            bookingService.createBooking(new CreateBookingCommand(
                    applicantId, scheduleId, InterviewStage.FINAL, "Follow-up"
            ));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Branch saveBranch() {
        Branch branch = new Branch();
        branch.setBranchCode("CONCURRENT-FOLLOW");
        branch.setBranchName("Concurrent Follow-up");
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch.setActive(true);
        return branchRepository.saveAndFlush(branch);
    }

    private User saveRecruiter(Branch branch) {
        User user = new User();
        user.setEmail("follow-up-concurrent@example.test");
        user.setPasswordHash("test-only-hash");
        user.setFullName("Concurrent Recruiter");
        user.setRole(Role.RECRUITER);
        user.setBranch(branch);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Applicant saveApplicant(Branch branch) {
        Applicant applicant = new Applicant();
        applicant.setFirstName("Concurrent");
        applicant.setLastName("Candidate");
        applicant.setEmail("concurrent-candidate@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setStatus(ApplicantStatus.FOR_FINAL_INTERVIEW);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private Schedule saveSchedule(Branch branch, LocalTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.now().plusDays(2));
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusHours(1));
        schedule.setSlotCapacity(1);
        schedule.setBookedCount(0);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return scheduleRepository.saveAndFlush(schedule);
    }
}
