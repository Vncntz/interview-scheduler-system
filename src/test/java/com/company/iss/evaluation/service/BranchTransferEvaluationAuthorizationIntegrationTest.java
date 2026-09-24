package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.applicant.service.ApplicantAssignmentGuard;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingLifecycleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingLifecycleHistoryRepository;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.service.BookingService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.evaluation.dto.CreateEvaluationCommand;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({ApplicantService.class, BookingService.class, InterviewEvaluationService.class})
class BranchTransferEvaluationAuthorizationIntegrationTest {

    @Autowired ApplicantService applicantService;
    @Autowired BookingService bookingService;
    @Autowired InterviewEvaluationService evaluationService;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingLifecycleHistoryRepository lifecycleHistoryRepository;
    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired PositionOpeningRepository positionRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean SecurityService securityService;
    @MockitoBean ApplicantAssignmentGuard applicantAssignmentGuard;
    @MockitoBean com.company.iss.shared.time.BusinessTime businessTime;

    @org.junit.jupiter.api.BeforeEach
    void setUpBusinessTime() {
        when(businessTime.snapshot()).thenReturn(
                com.company.iss.shared.time.BusinessTimeTestFactory.snapshotAt(
                        java.time.Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void transferMovesWorkflowAuthorityWithoutChangingHistoricalAppointment() {
        Branch branchA = saveBranch("TRANSFER-A");
        Branch branchB = saveBranch("TRANSFER-B");
        User recruiterA = saveUser("transfer-a@example.test", Role.RECRUITER, branchA);
        User recruiterB = saveUser("transfer-b@example.test", Role.RECRUITER, branchB);
        User admin = saveUser("transfer-admin@example.test", Role.ADMIN, null);
        PositionOpening position = savePosition();
        Schedule historicalSchedule = saveSchedule(branchA, recruiterA);
        Applicant applicant = saveApplicant(branchA, position);
        Booking booking = saveAttendedBooking(applicant, historicalSchedule);
        lifecycleHistoryRepository.append(BookingLifecycleHistory.record(
                booking,
                historicalSchedule,
                recruiterA,
                BookingLifecycleAction.ATTENDANCE_RECORDED,
                BookingStatus.CONFIRMED,
                BookingStatus.ATTENDED,
                LocalDateTime.of(2035, 6, 14, 10, 0)
        ));

        AtomicReference<User> actor = new AtomicReference<>(recruiterA);
        when(securityService.requireOperationsUser()).thenAnswer(invocation -> actor.get());
        when(securityService.requireOperationsUser(anyString())).thenAnswer(invocation -> actor.get());

        Long retainedBookingId = bookingService.findScopedById(booking.getId()).getId();
        actor.set(admin);
        assertEquals(booking.getId(), bookingService.findScopedById(booking.getId()).getId());

        applicantService.save(transferInput(applicant, branchB, position));

        assertEquals(booking.getId(), bookingService.findScopedById(booking.getId()).getId());
        assertEquals(List.of(retainedBookingId), bookingRepository.findOverdueUnevaluatedByApplicantBranch(
                branchB.getId(), BookingStatus.ATTENDED, LocalDate.of(2035, 6, 15), LocalTime.NOON
        ).stream().map(Booking::getId).toList());
        assertTrue(bookingRepository.findOverdueUnevaluatedByApplicantBranch(
                branchA.getId(), BookingStatus.ATTENDED, LocalDate.of(2035, 6, 15), LocalTime.NOON
        ).isEmpty());

        actor.set(recruiterA);
        assertThrows(AccessDeniedException.class, () -> bookingService.findScopedById(retainedBookingId));
        assertThrows(AccessDeniedException.class, () -> evaluationService.create(command(retainedBookingId)));
        assertUnchangedEvaluationState(booking.getId(), applicant.getId(), position.getId(), branchB.getId());

        actor.set(recruiterB);
        Booking currentBranchDetail = bookingService.findScopedById(retainedBookingId);
        assertEquals(branchA.getId(), currentBranchDetail.getSchedule().getBranch().getId());
        assertEquals(recruiterA.getId(), currentBranchDetail.getSchedule().getRecruiter().getId());
        assertEquals(InterviewStage.INITIAL, currentBranchDetail.getInterviewStage());

        evaluationService.create(command(retainedBookingId));

        Booking evaluated = bookingRepository.findDetailedById(retainedBookingId).orElseThrow();
        Applicant evaluatedApplicant = applicantRepository.findById(applicant.getId()).orElseThrow();
        PositionOpening evaluatedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertEquals(BookingStatus.PASSED, evaluated.getStatus());
        assertEquals(ApplicantStatus.PASSED, evaluatedApplicant.getStatus());
        assertEquals(branchB.getId(), evaluatedApplicant.getBranch().getId());
        assertEquals(branchA.getId(), evaluated.getSchedule().getBranch().getId());
        assertEquals(recruiterA.getId(), evaluated.getSchedule().getRecruiter().getId());
        assertEquals(InterviewStage.INITIAL, evaluated.getInterviewStage());
        assertEquals(1, evaluatedPosition.getInterviewEvaluationCount());
        assertEquals(1, evaluatedPosition.getPassedCount());
        assertEquals(1, lifecycleHistoryRepository.findByBookingIdOrderByOccurredAtAscIdAsc(retainedBookingId).size());
        assertEquals(1, evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(applicant.getId()).size());

        assertThrows(BusinessRuleViolationException.class, () -> evaluationService.create(command(retainedBookingId)));
        assertEquals(1, positionRepository.findById(position.getId()).orElseThrow().getInterviewEvaluationCount());
        assertEquals(1, positionRepository.findById(position.getId()).orElseThrow().getPassedCount());
    }

    private void assertUnchangedEvaluationState(
            Long bookingId,
            Long applicantId,
            Long positionId,
            Long expectedBranchId
    ) {
        assertEquals(BookingStatus.ATTENDED, bookingRepository.findById(bookingId).orElseThrow().getStatus());
        Applicant applicant = applicantRepository.findById(applicantId).orElseThrow();
        assertEquals(ApplicantStatus.INTERVIEWED, applicant.getStatus());
        assertEquals(expectedBranchId, applicant.getBranch().getId());
        PositionOpening position = positionRepository.findById(positionId).orElseThrow();
        assertEquals(0, position.getInterviewEvaluationCount());
        assertEquals(0, position.getPassedCount());
        assertTrue(evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(applicantId).isEmpty());
    }

    private Applicant transferInput(Applicant existing, Branch destination, PositionOpening position) {
        Applicant input = new Applicant();
        input.setId(existing.getId());
        input.setFirstName(existing.getFirstName());
        input.setMiddleName(existing.getMiddleName());
        input.setLastName(existing.getLastName());
        input.setEmail(existing.getEmail());
        input.setMobileNumber(existing.getMobileNumber());
        input.setBranch(destination);
        input.setPositionOpening(position);
        input.setSource(existing.getSource());
        input.setRemarks(existing.getRemarks());
        return input;
    }

    private CreateEvaluationCommand command(Long bookingId) {
        return new CreateEvaluationCommand(bookingId, 8, 9, 7, InterviewResult.PASS, "Valid transfer result");
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

    private PositionOpening savePosition() {
        Client client = new Client();
        client.setCompanyName("Transfer Authorization Client");
        client.setAddress("Test address");
        client.setActive(true);
        client = clientRepository.saveAndFlush(client);

        PositionOpening position = new PositionOpening();
        position.setTitle("Transfer Authorization Position");
        position.setClient(client);
        position.setWorkLocation("Test location");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(2);
        position.setAppliedCount(1);
        position.setInterviewEvaluationCount(0);
        position.setPassedCount(0);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        return positionRepository.saveAndFlush(position);
    }

    private Schedule saveSchedule(Branch branch, User recruiter) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.of(2035, 6, 14));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return scheduleRepository.saveAndFlush(schedule);
    }

    private Applicant saveApplicant(Branch branch, PositionOpening position) {
        Applicant applicant = new Applicant();
        applicant.setFirstName("Transfer");
        applicant.setLastName("Candidate");
        applicant.setEmail("transfer-candidate@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private Booking saveAttendedBooking(Applicant applicant, Schedule schedule) {
        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-BRANCH-TRANSFER");
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setStatus(BookingStatus.ATTENDED);
        booking.setBookedDateTime(LocalDateTime.of(2035, 6, 1, 9, 0));
        return bookingRepository.saveAndFlush(booking);
    }
}
