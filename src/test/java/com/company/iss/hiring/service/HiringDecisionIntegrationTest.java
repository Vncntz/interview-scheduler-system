package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.hiring.repository.HiringDecisionAuditRepository;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(HiringDecisionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HiringDecisionIntegrationTest {

    @Autowired HiringDecisionService service;
    @Autowired HiringDecisionRepository decisionRepository;
    @Autowired HiringDecisionAuditRepository auditRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired PositionOpeningRepository positionRepository;
    @Autowired UserRepository userRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean SecurityService securityService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = saveUser("hiring-admin@example.test");
        when(securityService.requireOperationsUser()).thenReturn(admin);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from hiring_decision_audits");
        decisionRepository.deleteAll();
        evaluationRepository.deleteAll();
        bookingRepository.deleteAll();
        applicantRepository.deleteAll();
        positionRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
        branchRepository.deleteAll();
    }

    @Test
    void concurrentAcceptsForFinalSlotHireExactlyOneApplicant() throws Exception {
        Branch branch = saveBranch("FINAL-SLOT");
        PositionOpening position = savePosition("Final Slot", 1);
        CandidateFixture first = savePassedCandidate("first-final-slot", branch, position);
        CandidateFixture second = savePassedCandidate("second-final-slot", branch, position);
        service.issueOffer(new IssueOfferCommand(first.applicantId(), first.evaluationId(), null));
        service.issueOffer(new IssueOfferCommand(second.applicantId(), second.evaluationId(), null));

        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> attemptHire(startGate, first.applicantId()));
            Future<Boolean> secondResult = executor.submit(() -> attemptHire(startGate, second.applicantId()));
            startGate.countDown();
            int successes = (firstResult.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (secondResult.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        }

        PositionOpening persistedPosition = positionRepository.findById(position.getId()).orElseThrow();
        assertEquals(1, persistedPosition.getHiredCount());
        assertEquals(PositionStatus.FILLED, persistedPosition.getStatus());
        List<ApplicantStatus> statuses = applicantRepository.findAll().stream()
                .map(Applicant::getStatus)
                .toList();
        assertEquals(1, statuses.stream().filter(status -> status == ApplicantStatus.HIRED).count());
        assertEquals(1, statuses.stream().filter(status -> status == ApplicantStatus.OFFERED).count());
        assertEquals(3, auditRepository.count());
    }

    @Test
    void concurrentDuplicateOfferActionProducesOneDecisionAndOneAudit() throws Exception {
        Branch branch = saveBranch("DUPLICATE-OFFER");
        PositionOpening position = savePosition("Duplicate Offer", 1);
        CandidateFixture candidate = savePassedCandidate("duplicate-offer", branch, position);
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptOffer(startGate, candidate));
            Future<Boolean> second = executor.submit(() -> attemptOffer(startGate, candidate));
            startGate.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
        }

        assertEquals(1, decisionRepository.count());
        assertEquals(1, auditRepository.count());
        assertEquals(ApplicantStatus.OFFERED, applicantRepository.findById(candidate.applicantId()).orElseThrow().getStatus());
    }

    @Test
    void outerRollbackRestoresOfferDecisionApplicantAndHeadcount() {
        Branch branch = saveBranch("ROLLBACK-HIRE");
        PositionOpening position = savePosition("Rollback Hire", 1);
        CandidateFixture candidate = savePassedCandidate("rollback-hire", branch, position);
        service.issueOffer(new IssueOfferCommand(candidate.applicantId(), candidate.evaluationId(), null));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            service.acceptAndHire(new HiringActionCommand(candidate.applicantId(), "Accepted"));
            status.setRollbackOnly();
        });

        assertEquals(
                HiringDecisionStatus.OFFERED,
                decisionRepository.findAll().getFirst().getStatus()
        );
        assertEquals(ApplicantStatus.OFFERED, applicantRepository.findById(candidate.applicantId()).orElseThrow().getStatus());
        assertEquals(0, positionRepository.findById(position.getId()).orElseThrow().getHiredCount());
        assertEquals(1, auditRepository.count());
    }

    private boolean attemptHire(CountDownLatch startGate, Long applicantId) throws InterruptedException {
        startGate.await(5, TimeUnit.SECONDS);
        try {
            service.acceptAndHire(new HiringActionCommand(applicantId, null));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean attemptOffer(CountDownLatch startGate, CandidateFixture candidate) throws InterruptedException {
        startGate.await(5, TimeUnit.SECONDS);
        try {
            service.issueOffer(new IssueOfferCommand(candidate.applicantId(), candidate.evaluationId(), null));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Branch saveBranch(String code) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(code);
        branch.setAddress("Address");
        branch.setCity("City");
        branch.setProvince("Province");
        branch.setActive(true);
        return branchRepository.saveAndFlush(branch);
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName("Hiring Admin");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private PositionOpening savePosition(String title, int headcount) {
        Client client = new Client();
        client.setCompanyName(title + " Client");
        client.setAddress("Address");
        client.setActive(true);
        client = clientRepository.saveAndFlush(client);

        PositionOpening position = new PositionOpening();
        position.setTitle(title);
        position.setClient(client);
        position.setWorkLocation("Singapore");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(headcount);
        position.setAppliedCount(2);
        position.setInterviewedCount(2);
        position.setPassedCount(2);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        return positionRepository.saveAndFlush(position);
    }

    private CandidateFixture savePassedCandidate(String suffix, Branch branch, PositionOpening position) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(suffix);
        applicant.setLastName("Applicant");
        applicant.setEmail(suffix + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.PASSED);
        applicant.setActive(true);
        applicant = applicantRepository.saveAndFlush(applicant);

        Booking booking = new Booking();
        booking.setBookingReference("BK-" + suffix);
        booking.setApplicant(applicant);
        booking.setStatus(BookingStatus.PASSED);
        booking = bookingRepository.saveAndFlush(booking);

        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setApplicant(applicant);
        evaluation.setBooking(booking);
        evaluation.setCommunicationScore(8);
        evaluation.setTechnicalScore(8);
        evaluation.setAttitudeScore(8);
        evaluation.setResult(InterviewResult.PASS);
        evaluation = evaluationRepository.saveAndFlush(evaluation);
        return new CandidateFixture(applicant.getId(), evaluation.getId());
    }

    private record CandidateFixture(Long applicantId, Long evaluationId) {
    }
}
