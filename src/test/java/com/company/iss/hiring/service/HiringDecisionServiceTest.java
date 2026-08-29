package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.branch.entity.Branch;
import com.company.iss.client.entity.Client;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionAction;
import com.company.iss.hiring.entity.HiringDecisionAudit;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.hiring.event.ApplicantHiredEvent;
import com.company.iss.hiring.event.JobOfferIssuedEvent;
import com.company.iss.hiring.repository.HiringDecisionAuditRepository;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HiringDecisionServiceTest {

    @Mock HiringDecisionRepository decisionRepository;
    @Mock HiringDecisionAuditRepository auditRepository;
    @Mock ApplicantRepository applicantRepository;
    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock PositionOpeningRepository positionRepository;
    @Mock SecurityService securityService;
    @Mock ApplicationEventPublisher eventPublisher;

    private HiringDecisionService service;

    @BeforeEach
    void setUp() {
        service = new HiringDecisionService(
                decisionRepository,
                auditRepository,
                applicantRepository,
                evaluationRepository,
                positionRepository,
                securityService,
                eventPublisher
        );
    }

    @Test
    void issueOfferRequiresAuthoritativePassedStateAndWritesOneAuditAndEvent() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        InterviewEvaluation evaluation = passedEvaluation(20L, applicant);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());
        when(evaluationRepository.findDetailedById(20L)).thenReturn(Optional.of(evaluation));
        when(decisionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            HiringDecision decision = invocation.getArgument(0);
            decision.setId(30L);
            return decision;
        });

        var result = service.issueOffer(new IssueOfferCommand(10L, 20L, "  Competitive offer  "));

        assertEquals(HiringDecisionStatus.OFFERED, result.status());
        assertEquals(ApplicantStatus.OFFERED, applicant.getStatus());
        assertEquals("Competitive offer", result.offeredRemarks());
        verify(applicantRepository).findByIdForUpdate(10L);
        verify(decisionRepository).findByApplicantIdForUpdate(10L);
        verify(auditRepository).append(any(HiringDecisionAudit.class));
        verify(eventPublisher).publishEvent(new JobOfferIssuedEvent(30L));
    }

    @Test
    void reissuingSameOutstandingOfferIsIdempotent() {
        User actor = admin();
        HiringDecision decision = offeredDecision(30L, eligibleApplicant(10L, 1L), actor);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(decision.getApplicant()));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        var result = service.issueOffer(new IssueOfferCommand(10L, 20L, "ignored repeat"));

        assertEquals(30L, result.decisionId());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
        verify(decisionRepository, never()).saveAndFlush(any());
    }

    @Test
    void existingOfferCannotBeReissuedAgainstDifferentEvaluation() {
        User actor = admin();
        HiringDecision decision = offeredDecision(30L, eligibleApplicant(10L, 1L), actor);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(decision.getApplicant()));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 21L, null))
        );

        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
    }

    @Test
    void acceptingOfferLocksPositionInOrderAndConsumesFinalSlotExactlyOnce() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(ApplicantStatus.OFFERED);
        PositionOpening position = applicant.getPositionOpening();
        position.setRequiredHeadcount(2);
        position.setHiredCount(1);
        HiringDecision decision = offeredDecision(30L, applicant, actor);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));
        when(positionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(position));

        var result = service.acceptAndHire(new HiringActionCommand(10L, "Accepted by candidate"));

        assertEquals(HiringDecisionStatus.HIRED, result.status());
        assertEquals(ApplicantStatus.HIRED, applicant.getStatus());
        assertEquals(2, position.getHiredCount());
        assertEquals(PositionStatus.FILLED, position.getStatus());
        verify(applicantRepository).findByIdForUpdate(10L);
        verify(decisionRepository).findByApplicantIdForUpdate(10L);
        verify(positionRepository).findByIdForUpdate(100L);
        verify(eventPublisher).publishEvent(new ApplicantHiredEvent(30L));

        ArgumentCaptor<HiringDecisionAudit> auditCaptor = ArgumentCaptor.forClass(HiringDecisionAudit.class);
        verify(auditRepository).append(auditCaptor.capture());
        assertEquals(HiringDecisionAction.ACCEPTED_AND_HIRED, auditCaptor.getValue().getAction());
    }

    @Test
    void unavailableFinalSlotDoesNotPartiallyResolveOutstandingOffer() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(ApplicantStatus.OFFERED);
        PositionOpening position = applicant.getPositionOpening();
        position.setHiredCount(1);
        position.setRequiredHeadcount(1);
        position.setStatus(PositionStatus.FILLED);
        HiringDecision decision = offeredDecision(30L, applicant, actor);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));
        when(positionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(position));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.acceptAndHire(new HiringActionCommand(10L, null))
        );

        assertEquals(HiringDecisionStatus.OFFERED, decision.getStatus());
        assertEquals(ApplicantStatus.OFFERED, applicant.getStatus());
        assertEquals(1, position.getHiredCount());
        verify(decisionRepository, never()).save(any());
        verify(applicantRepository, never()).save(any());
        verify(positionRepository, never()).save(any());
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    @Test
    void repeatingSameTerminalActionIsIdempotentWithoutHeadcountOrAudit() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(ApplicantStatus.HIRED);
        HiringDecision decision = offeredDecision(30L, applicant, actor);
        decision.setStatus(HiringDecisionStatus.HIRED);
        decision.setResolvedBy(actor);
        decision.setResolvedAt(LocalDateTime.now());
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        var result = service.acceptAndHire(new HiringActionCommand(10L, null));

        assertEquals(HiringDecisionStatus.HIRED, result.status());
        verifyNoInteractions(positionRepository, auditRepository, eventPublisher);
        verify(decisionRepository, never()).save(any());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    void repeatedDeclineDoesNotRequireAnotherReasonOrWriteAnotherAudit() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(ApplicantStatus.OFFER_DECLINED);
        HiringDecision decision = offeredDecision(30L, applicant, actor);
        decision.setStatus(HiringDecisionStatus.DECLINED);
        decision.setResolvedBy(actor);
        decision.setResolvedAt(LocalDateTime.now());
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        var result = service.decline(new HiringActionCommand(10L, null));

        assertEquals(HiringDecisionStatus.DECLINED, result.status());
        verifyNoInteractions(auditRepository, eventPublisher, positionRepository);
    }

    @Test
    void declineAndWithdrawRequireReasonsAndApplyTheirDistinctTerminalStates() {
        User actor = admin();
        Applicant declinedApplicant = eligibleApplicant(10L, 1L);
        declinedApplicant.setStatus(ApplicantStatus.OFFERED);
        HiringDecision declinedDecision = offeredDecision(30L, declinedApplicant, actor);
        Applicant withdrawnApplicant = eligibleApplicant(11L, 1L);
        withdrawnApplicant.setStatus(ApplicantStatus.OFFERED);
        HiringDecision withdrawnDecision = offeredDecision(31L, withdrawnApplicant, actor);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(declinedApplicant));
        when(applicantRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(withdrawnApplicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(declinedDecision));
        when(decisionRepository.findByApplicantIdForUpdate(11L)).thenReturn(Optional.of(withdrawnDecision));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.decline(new HiringActionCommand(10L, " "))
        );
        service.decline(new HiringActionCommand(10L, "Candidate declined"));
        service.withdraw(new HiringActionCommand(11L, "Offer terms changed"));

        assertEquals(HiringDecisionStatus.DECLINED, declinedDecision.getStatus());
        assertEquals(ApplicantStatus.OFFER_DECLINED, declinedApplicant.getStatus());
        assertEquals(HiringDecisionStatus.WITHDRAWN, withdrawnDecision.getStatus());
        assertEquals(ApplicantStatus.WITHDRAWN, withdrawnApplicant.getStatus());
        verify(auditRepository, org.mockito.Mockito.times(2)).append(any(HiringDecisionAudit.class));
    }

    @Test
    void differentTerminalActionIsRejectedWithoutWrites() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(ApplicantStatus.HIRED);
        HiringDecision decision = offeredDecision(30L, applicant, actor);
        decision.setStatus(HiringDecisionStatus.HIRED);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        assertThrows(
                BusinessRuleViolationException.class,
                () -> service.decline(new HiringActionCommand(10L, "Candidate declined"))
        );

        verifyNoInteractions(auditRepository, eventPublisher, positionRepository);
    }

    @Test
    void recruiterCannotMutateGuessedApplicantFromAnotherBranch() {
        User recruiter = recruiter(1L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findByIdAndBranchIdForUpdate(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 20L, null))
        );

        verify(decisionRepository, never()).findByApplicantIdForUpdate(any());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
    }

    @Test
    void recruiterEligibleListingUsesBranchScopedQueryOnly() {
        User recruiter = recruiter(1L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(decisionRepository.findEligibleEvaluationsByBranchId(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.findEligibleCandidates());

        verify(decisionRepository).findEligibleEvaluationsByBranchId(1L);
        verify(decisionRepository, never()).findEligibleEvaluations();
    }

    @Test
    void recruiterDecisionListingsUseOnlyBranchScopedQueries() {
        User recruiter = recruiter(1L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(decisionRepository.findByStatusAndApplicantBranchIdOrderByOfferedAtDesc(
                HiringDecisionStatus.OFFERED, 1L
        )).thenReturn(List.of());
        when(decisionRepository.findByStatusInAndApplicantBranchIdOrderByResolvedAtDesc(any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(List.of());

        assertEquals(List.of(), service.findOutstandingDecisions());
        assertEquals(List.of(), service.findCompletedDecisions());

        verify(decisionRepository, never()).findByStatusOrderByOfferedAtDesc(any());
        verify(decisionRepository, never()).findByStatusInOrderByResolvedAtDesc(any());
    }

    private User admin() {
        User actor = new User();
        actor.setId(1L);
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        actor.setFullName("Admin User");
        return actor;
    }

    private User recruiter(Long branchId) {
        User actor = admin();
        actor.setRole(Role.RECRUITER);
        actor.setBranch(branch(branchId));
        return actor;
    }

    private Applicant eligibleApplicant(Long id, Long branchId) {
        PositionOpening position = new PositionOpening();
        position.setId(100L);
        position.setTitle("Software Engineer");
        position.setWorkLocation("Singapore");
        position.setRequiredHeadcount(2);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        Client client = new Client();
        client.setCompanyName("Example Client");
        position.setClient(client);

        Applicant applicant = new Applicant();
        applicant.setId(id);
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setEmail("alex@example.test");
        applicant.setActive(true);
        applicant.setStatus(ApplicantStatus.PASSED);
        applicant.setBranch(branch(branchId));
        applicant.setPositionOpening(position);
        return applicant;
    }

    private InterviewEvaluation passedEvaluation(Long id, Applicant applicant) {
        Booking booking = new Booking();
        booking.setId(40L);
        booking.setApplicant(applicant);
        booking.setStatus(BookingStatus.PASSED);
        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setId(id);
        evaluation.setApplicant(applicant);
        evaluation.setBooking(booking);
        evaluation.setResult(InterviewResult.PASS);
        evaluation.setEvaluationDate(LocalDateTime.now());
        return evaluation;
    }

    private HiringDecision offeredDecision(Long id, Applicant applicant, User actor) {
        InterviewEvaluation evaluation = passedEvaluation(20L, applicant);
        HiringDecision decision = new HiringDecision();
        decision.setId(id);
        decision.setApplicant(applicant);
        decision.setEvaluation(evaluation);
        decision.setPosition(applicant.getPositionOpening());
        decision.setStatus(HiringDecisionStatus.OFFERED);
        decision.setOfferedBy(actor);
        decision.setOfferedAt(LocalDateTime.now());
        return decision;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setBranchName("Branch " + id);
        return branch;
    }
}
