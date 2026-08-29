package com.company.iss.hiring.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
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
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionAction;
import com.company.iss.hiring.entity.HiringDecisionAudit;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class HiringDecisionRepositoryTest {

    @Autowired HiringDecisionRepository decisionRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired PositionOpeningRepository positionRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired UserRepository userRepository;
    @Autowired HiringDecisionAuditRepository auditRepository;
    @Autowired EntityManager entityManager;

    @Test
    void applicantCanHaveOnlyOneHiringDecision() {
        TestData data = persistEligibleCandidate("unique-applicant");
        InterviewEvaluation secondEvaluation = evaluation(
                data.applicant(),
                bookingRepository.save(booking(data.applicant(), "BK-SECOND-DECISION"))
        );
        secondEvaluation = evaluationRepository.saveAndFlush(secondEvaluation);
        decisionRepository.saveAndFlush(decision(data, data.evaluation()));

        InterviewEvaluation persistedSecondEvaluation = secondEvaluation;
        assertThrows(
                DataIntegrityViolationException.class,
                () -> decisionRepository.saveAndFlush(decision(data, persistedSecondEvaluation))
        );
    }

    @Test
    void evaluationCanBelongToOnlyOneHiringDecision() {
        TestData first = persistEligibleCandidate("unique-evaluation-first");
        TestData second = persistEligibleCandidate("unique-evaluation-second");
        decisionRepository.saveAndFlush(decision(first, first.evaluation()));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> decisionRepository.saveAndFlush(decision(second, first.evaluation()))
        );
    }

    @Test
    void eligibleFetchIsBranchScopedAndExcludesExistingDecision() {
        TestData data = persistEligibleCandidate("eligible-query");

        assertEquals(1, decisionRepository.findEligibleEvaluations().size());
        assertEquals(1, decisionRepository.findEligibleEvaluationsByBranchId(data.branch().getId()).size());
        assertEquals(0, decisionRepository.findEligibleEvaluationsByBranchId(data.branch().getId() + 100).size());

        decisionRepository.saveAndFlush(decision(data, data.evaluation()));

        assertEquals(0, decisionRepository.findEligibleEvaluations().size());
    }

    @Test
    void persistedAuditIsIgnoredByDirtyChecking() throws ReflectiveOperationException {
        TestData data = persistEligibleCandidate("immutable-audit");
        HiringDecision decision = decisionRepository.saveAndFlush(decision(data, data.evaluation()));
        HiringDecisionAudit audit = HiringDecisionAudit.record(
                decision,
                HiringDecisionAction.OFFER_ISSUED,
                null,
                HiringDecisionStatus.OFFERED,
                data.actor(),
                LocalDateTime.now(),
                "Original remarks"
        );
        auditRepository.append(audit);
        entityManager.flush();
        entityManager.clear();

        HiringDecisionAudit persisted = auditRepository
                .findByDecisionIdOrderByOccurredAtAsc(decision.getId())
                .getFirst();
        Field remarks = HiringDecisionAudit.class.getDeclaredField("remarks");
        remarks.setAccessible(true);
        remarks.set(persisted, "Tampered remarks");
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                "Original remarks",
                auditRepository.findByDecisionIdOrderByOccurredAtAsc(decision.getId()).getFirst().getRemarks()
        );
    }

    @Test
    void auditRepositoryContractExposesOnlyAppendAndQueries() {
        Set<String> methodNames = Arrays.stream(HiringDecisionAuditRepository.class.getMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.containsAll(Set.of(
                "append",
                "count",
                "findByDecisionIdOrderByOccurredAtAsc"
        )));
        assertFalse(CrudRepository.class.isAssignableFrom(HiringDecisionAuditRepository.class));
        assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("save")
                || name.startsWith("delete")
                || name.startsWith("update")));
    }

    private TestData persistEligibleCandidate(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode("B-" + Integer.toHexString(suffix.hashCode()));
        branch.setBranchName("Branch " + suffix);
        branch.setCity("City");
        branch.setProvince("Province");
        branch.setAddress("Address");
        branch.setActive(true);
        branch = branchRepository.save(branch);

        Client client = new Client();
        client.setCompanyName("Client " + suffix);
        client.setAddress("Address");
        client.setActive(true);
        client = clientRepository.save(client);

        PositionOpening position = new PositionOpening();
        position.setTitle("Engineer " + suffix);
        position.setClient(client);
        position.setWorkLocation("Singapore");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(1);
        position.setAppliedCount(1);
        position.setInterviewedCount(1);
        position.setPassedCount(1);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        position = positionRepository.save(position);

        Applicant applicant = new Applicant();
        applicant.setBranch(branch);
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setEmail(suffix + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.PASSED);
        applicant.setActive(true);
        applicant = applicantRepository.save(applicant);

        User actor = new User();
        actor.setEmail("actor-" + suffix + "@example.test");
        actor.setPasswordHash("test-only-hash");
        actor.setFullName("Test Actor");
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        actor = userRepository.save(actor);

        Booking booking = bookingRepository.save(booking(applicant, "BK-" + suffix));
        InterviewEvaluation evaluation = evaluationRepository.saveAndFlush(evaluation(applicant, booking));
        return new TestData(branch, applicant, position, actor, evaluation);
    }

    private Booking booking(Applicant applicant, String reference) {
        Booking booking = new Booking();
        booking.setBookingReference(reference);
        booking.setApplicant(applicant);
        booking.setStatus(BookingStatus.PASSED);
        return booking;
    }

    private InterviewEvaluation evaluation(Applicant applicant, Booking booking) {
        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setApplicant(applicant);
        evaluation.setBooking(booking);
        evaluation.setCommunicationScore(8);
        evaluation.setTechnicalScore(9);
        evaluation.setAttitudeScore(8);
        evaluation.setResult(InterviewResult.PASS);
        return evaluation;
    }

    private HiringDecision decision(TestData data, InterviewEvaluation evaluation) {
        HiringDecision decision = new HiringDecision();
        decision.setApplicant(data.applicant());
        decision.setEvaluation(evaluation);
        decision.setPosition(data.position());
        decision.setStatus(HiringDecisionStatus.OFFERED);
        decision.setOfferedBy(data.actor());
        decision.setOfferedAt(LocalDateTime.now());
        return decision;
    }

    private record TestData(
            Branch branch,
            Applicant applicant,
            PositionOpening position,
            User actor,
            InterviewEvaluation evaluation
    ) {
    }
}
