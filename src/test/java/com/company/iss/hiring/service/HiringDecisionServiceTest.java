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
import com.company.iss.hiring.dto.CompletedDecisionSort;
import com.company.iss.hiring.dto.CompletedDecisionSortOrder;
import com.company.iss.hiring.dto.HiringWorklistFilter;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.dto.OfferDeadlineFilter;
import com.company.iss.hiring.dto.OutstandingOfferFilter;
import com.company.iss.hiring.dto.OutstandingDecisionSort;
import com.company.iss.hiring.dto.OutstandingDecisionSortOrder;
import com.company.iss.hiring.config.OfferDeadlineProperties;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionAction;
import com.company.iss.hiring.entity.HiringDecisionAudit;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.hiring.event.ApplicantHiredEvent;
import com.company.iss.hiring.event.JobOfferIssuedEvent;
import com.company.iss.hiring.exception.HiringDecisionException;
import com.company.iss.hiring.repository.HiringDecisionAuditRepository;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HiringDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T02:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

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
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setTimestampZone(ZoneOffset.UTC);
        service = new HiringDecisionService(
                decisionRepository,
                auditRepository,
                applicantRepository,
                evaluationRepository,
                positionRepository,
                securityService,
                eventPublisher,
                new OfferDeadlinePolicy(Clock.fixed(NOW, ZoneOffset.UTC), properties)
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
    void issueOfferPersistsStrictlyFutureResponseDeadlineAtMicrosecondPrecision() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        InterviewEvaluation evaluation = passedEvaluation(20L, applicant);
        LocalDateTime deadline = NOW_LOCAL.plusDays(2).withNano(123_456_789);
        LocalDateTime normalizedDeadline = deadline.withNano(123_456_000);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());
        when(evaluationRepository.findDetailedById(20L)).thenReturn(Optional.of(evaluation));
        when(decisionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.issueOffer(new IssueOfferCommand(10L, 20L, deadline, null));

        assertEquals(normalizedDeadline, result.responseDueAt());
        assertEquals(com.company.iss.hiring.entity.OfferDeadlineState.ON_TRACK, result.deadlineState());
        ArgumentCaptor<HiringDecision> decision = ArgumentCaptor.forClass(HiringDecision.class);
        verify(decisionRepository).saveAndFlush(decision.capture());
        assertEquals(normalizedDeadline, decision.getValue().getResponseDueAt());
    }

    @Test
    void issueOfferRejectsNonFutureDeadlineBeforePersistence() {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());

        HiringDecisionException exception = assertThrows(HiringDecisionException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 20L, NOW_LOCAL, null)));

        assertEquals("Response deadline must be in the future.", exception.getMessage());
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(applicantRepository, never()).save(any());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(
            value = ApplicantStatus.class,
            names = {"FOR_FINAL_INTERVIEW", "FOR_CLIENT_INTERVIEW"}
    )
    void pendingInterviewStageCannotReceiveOffer(ApplicantStatus pendingStatus) {
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        applicant.setStatus(pendingStatus);
        PositionOpening position = applicant.getPositionOpening();
        InterviewEvaluation evaluation = passedEvaluation(20L, applicant);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());
        when(evaluationRepository.findDetailedById(20L)).thenReturn(Optional.of(evaluation));

        HiringDecisionException exception = assertThrows(
                HiringDecisionException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 20L, null))
        );

        assertEquals("Only active applicants with a passed result are eligible for an offer.", exception.getMessage());
        assertEquals(pendingStatus, applicant.getStatus());
        assertEquals(0, position.getHiredCount());
        assertEquals(PositionStatus.OPEN, position.getStatus());
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(decisionRepository, never()).save(any());
        verify(applicantRepository, never()).save(any());
        verify(positionRepository, never()).save(any());
        verifyNoInteractions(auditRepository, eventPublisher);
    }

    @Test
    void reissuingDatabaseEquivalentOutstandingOfferIsIdempotentAfterDeadlinePasses() {
        User actor = admin();
        HiringDecision decision = offeredDecision(30L, eligibleApplicant(10L, 1L), actor);
        LocalDateTime storedDeadline = NOW_LOCAL.minusHours(1).withNano(123_456_000);
        LocalDateTime originalDeadline = storedDeadline.withNano(123_456_789);
        decision.setResponseDueAt(storedDeadline);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(decision.getApplicant()));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        var result = service.issueOffer(new IssueOfferCommand(10L, 20L, originalDeadline, "ignored repeat"));

        assertEquals(30L, result.decisionId());
        assertEquals(storedDeadline, result.responseDueAt());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    void reissuingOutstandingOfferWithDifferentMicrosecondDeadlineIsRejectedAsConflict() {
        User actor = admin();
        HiringDecision decision = offeredDecision(30L, eligibleApplicant(10L, 1L), actor);
        LocalDateTime storedDeadline = NOW_LOCAL.plusHours(2).withNano(123_456_000);
        LocalDateTime differentDeadline = storedDeadline.withNano(123_457_000);
        decision.setResponseDueAt(storedDeadline);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(decision.getApplicant()));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        HiringDecisionException exception = assertThrows(HiringDecisionException.class,
                () -> service.issueOffer(new IssueOfferCommand(
                        10L, 20L, differentDeadline, "changed deadline")));

        assertEquals("This applicant already has a hiring decision and cannot receive another offer.",
                exception.getMessage());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(applicantRepository, never()).save(any());
    }

    @Test
    void issueOfferRejectsDeadlineThatIsNotFutureAtDatabasePrecision() {
        Instant boundaryNow = Instant.parse("2026-09-13T02:00:00.123456500Z");
        OfferDeadlineProperties properties = new OfferDeadlineProperties();
        properties.setTimestampZone(ZoneOffset.UTC);
        service = new HiringDecisionService(
                decisionRepository,
                auditRepository,
                applicantRepository,
                evaluationRepository,
                positionRepository,
                securityService,
                eventPublisher,
                new OfferDeadlinePolicy(Clock.fixed(boundaryNow, ZoneOffset.UTC), properties)
        );
        User actor = admin();
        Applicant applicant = eligibleApplicant(10L, 1L);
        LocalDateTime submittedDeadline = LocalDateTime.ofInstant(boundaryNow, ZoneOffset.UTC)
                .withNano(123_456_900);
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());

        HiringDecisionException exception = assertThrows(HiringDecisionException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 20L, submittedDeadline, null)));

        assertEquals("Response deadline must be in the future.", exception.getMessage());
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(applicantRepository, never()).save(any());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
    }

    @Test
    void reissuingOutstandingOfferWithDifferentInvalidDeadlineReturnsValidationError() {
        User actor = admin();
        HiringDecision decision = offeredDecision(30L, eligibleApplicant(10L, 1L), actor);
        decision.setResponseDueAt(NOW_LOCAL.minusHours(1));
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(decision.getApplicant()));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));

        HiringDecisionException exception = assertThrows(HiringDecisionException.class,
                () -> service.issueOffer(new IssueOfferCommand(10L, 20L, NOW_LOCAL, null)));

        assertEquals("Response deadline must be in the future.", exception.getMessage());
        verifyNoInteractions(evaluationRepository, auditRepository, eventPublisher);
        verify(decisionRepository, never()).saveAndFlush(any());
        verify(applicantRepository, never()).save(any());
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
        decision.setResponseDueAt(NOW_LOCAL.minusHours(1));
        when(securityService.requireOperationsUser()).thenReturn(actor);
        when(applicantRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.of(decision));
        when(positionRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(position));

        var result = service.acceptAndHire(new HiringActionCommand(10L, "Accepted by candidate"));

        assertEquals(HiringDecisionStatus.HIRED, result.status());
        assertEquals(ApplicantStatus.HIRED, applicant.getStatus());
        assertEquals(2, position.getHiredCount());
        assertEquals(PositionStatus.FILLED, position.getStatus());
        assertEquals(NOW_LOCAL.minusHours(1), result.responseDueAt());
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
    void recruiterCanIssueDeadlineOfferInsideAuthoritativeBranch() {
        User recruiter = recruiter(1L);
        Applicant applicant = eligibleApplicant(10L, 1L);
        InterviewEvaluation evaluation = passedEvaluation(20L, applicant);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findByIdAndBranchIdForUpdate(10L, 1L)).thenReturn(Optional.of(applicant));
        when(decisionRepository.findByApplicantIdForUpdate(10L)).thenReturn(Optional.empty());
        when(evaluationRepository.findDetailedById(20L)).thenReturn(Optional.of(evaluation));
        when(decisionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.issueOffer(new IssueOfferCommand(
                10L, 20L, NOW_LOCAL.plusHours(12), null));

        assertEquals(NOW_LOCAL.plusHours(12), result.responseDueAt());
        assertEquals(HiringDecisionStatus.OFFERED, result.status());
        verify(applicantRepository).findByIdAndBranchIdForUpdate(10L, 1L);
    }

    @Test
    void recruiterEligiblePageAndCountUseBranchScopeAndExactWindow() {
        User recruiter = recruiter(1L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(decisionRepository.findEligibleEvaluationPage(eq(1L), eq("alex"), any())).thenReturn(List.of());
        when(decisionRepository.countEligibleEvaluationPage(1L, "alex")).thenReturn(0L);

        assertEquals(List.of(), service.findEligiblePage(
                new HiringWorklistFilter("  AlEx  "), 25, 10, List.of()
        ));
        assertEquals(0L, service.countEligible(new HiringWorklistFilter("  AlEx  ")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(decisionRepository).findEligibleEvaluationPage(eq(1L), eq("alex"), pageable.capture());
        assertEquals(25L, pageable.getValue().getOffset());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals("evaluationDate: DESC,id: DESC", pageable.getValue().getSort().toString());
        verify(decisionRepository).countEligibleEvaluationPage(1L, "alex");
    }

    @Test
    void recruiterDecisionPagesAndCountsUseOnlyBranchScopedQueries() {
        User recruiter = recruiter(1L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(decisionRepository.findDecisionPage(eq(1L), any(), isNull(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        when(decisionRepository.countDecisionPage(eq(1L), any(), isNull(), anyBoolean(), any()))
                .thenReturn(0L);

        when(decisionRepository.findOutstandingDecisionPageByDeadlinePriority(
                eq(1L), isNull(), eq(false), eq("ALL"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), any())).thenReturn(List.of());
        when(decisionRepository.countOutstandingDecisions(
                eq(1L), isNull(), eq(false), eq("ALL"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)))).thenReturn(0L);

        assertEquals(List.of(), service.findOutstandingPage(OutstandingOfferFilter.empty(), 0, 50, List.of()));
        assertEquals(List.of(), service.findCompletedPage(HiringWorklistFilter.empty(), 0, 50, List.of()));
        assertEquals(0L, service.countOutstanding(OutstandingOfferFilter.empty()));
        assertEquals(0L, service.countCompleted(HiringWorklistFilter.empty()));

        verify(decisionRepository).findDecisionPage(
                eq(1L), any(), isNull(), eq(false), eq(List.of(HiringDecisionStatus.OFFERED)), any()
        );
        verify(decisionRepository).countDecisionPage(
                eq(1L), any(), isNull(), eq(false), eq(List.of(HiringDecisionStatus.OFFERED))
        );
        verify(decisionRepository).findOutstandingDecisionPageByDeadlinePriority(
                eq(1L), isNull(), eq(false), eq("ALL"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), any());
        verify(decisionRepository).countOutstandingDecisions(
                1L, null, false, "ALL", NOW_LOCAL, NOW_LOCAL.plusHours(24));
    }

    @Test
    void outstandingDeadlineFilterAndWhitelistedResponseSortReachRepositoryWithExactBoundaries() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        when(decisionRepository.findOutstandingDecisionPage(
                isNull(), eq("offered"), eq(true), eq("OVERDUE"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), any())).thenReturn(List.of());

        service.findOutstandingPage(
                new OutstandingOfferFilter(" Offered ", OfferDeadlineFilter.OVERDUE), 7, 12,
                List.of(new OutstandingDecisionSortOrder(
                        OutstandingDecisionSort.RESPONSE_DUE, Sort.Direction.DESC)));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(decisionRepository).findOutstandingDecisionPage(
                isNull(), eq("offered"), eq(true), eq("OVERDUE"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), page.capture());
        assertEquals(7L, page.getValue().getOffset());
        assertEquals(12, page.getValue().getPageSize());
        assertEquals("responseDueAt: DESC,id: ASC", page.getValue().getSort().toString());
    }

    @Test
    void outstandingSearchDoesNotTreatTerminalStatusTextAsAnOfferedMatch() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        when(decisionRepository.findOutstandingDecisionPageByDeadlinePriority(
                isNull(), eq("hired"), eq(false), eq("ALL"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), any())).thenReturn(List.of());

        service.findOutstandingPage(
                new OutstandingOfferFilter(" HiReD ", OfferDeadlineFilter.ALL), 0, 20, List.of());

        verify(decisionRepository).findOutstandingDecisionPageByDeadlinePriority(
                isNull(), eq("hired"), eq(false), eq("ALL"), eq(NOW_LOCAL),
                eq(NOW_LOCAL.plusHours(24)), any());
    }

    @Test
    void worklistRejectsInvalidWindowsAndOversizedSearchBeforeQueryingRepository() {
        when(securityService.requireOperationsUser()).thenReturn(admin());

        assertThrows(IllegalArgumentException.class,
                () -> service.findEligiblePage(HiringWorklistFilter.empty(), -1, 10, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.findOutstandingPage(OutstandingOfferFilter.empty(), 0, 101, List.of()));
        assertThrows(BusinessRuleViolationException.class,
                () -> service.countCompleted(new HiringWorklistFilter("x".repeat(101))));

        verifyNoInteractions(decisionRepository);
    }

    @Test
    void adminCompletedSearchMatchesOnlyRelevantStatusAndUsesWhitelistedStableSort() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        when(decisionRepository.findDecisionPage(
                isNull(), any(), eq("hired"), eq(true), eq(List.of(HiringDecisionStatus.HIRED)), any()
        )).thenReturn(List.of());

        service.findCompletedPage(
                new HiringWorklistFilter(" HiReD "), 0, 20,
                List.of(new CompletedDecisionSortOrder(CompletedDecisionSort.APPLICANT, Sort.Direction.DESC))
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(decisionRepository).findDecisionPage(
                isNull(), any(), eq("hired"), eq(true), eq(List.of(HiringDecisionStatus.HIRED)), pageable.capture()
        );
        assertEquals(
                "applicant.lastName: DESC,applicant.firstName: DESC,id: ASC",
                pageable.getValue().getSort().toString()
        );
    }

    @Test
    void applicantIsDeniedEveryWorklistPageAndCountBeforeRepositoryAccess() {
        when(securityService.requireOperationsUser()).thenThrow(new AccessDeniedException("operations only"));

        assertThrows(AccessDeniedException.class,
                () -> service.findEligiblePage(HiringWorklistFilter.empty(), 0, 10, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> service.countEligible(HiringWorklistFilter.empty()));
        assertThrows(AccessDeniedException.class,
                () -> service.findOutstandingPage(OutstandingOfferFilter.empty(), 0, 10, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> service.countOutstanding(OutstandingOfferFilter.empty()));
        assertThrows(AccessDeniedException.class,
                () -> service.findCompletedPage(HiringWorklistFilter.empty(), 0, 10, List.of()));
        assertThrows(AccessDeniedException.class,
                () -> service.countCompleted(HiringWorklistFilter.empty()));

        verifyNoInteractions(decisionRepository);
    }

    @Test
    void worklistRejectsTooLargeOffsetAndNullSortOrder() {
        when(securityService.requireOperationsUser()).thenReturn(admin());

        assertThrows(IllegalArgumentException.class,
                () -> service.findEligiblePage(
                        HiringWorklistFilter.empty(), (long) Integer.MAX_VALUE + 1, 10, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.findCompletedPage(
                        HiringWorklistFilter.empty(), 0, 10,
                        java.util.Collections.singletonList(null)));

        verifyNoInteractions(decisionRepository);
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
        InterviewEvaluation evaluation = InterviewEvaluation.record(
                booking, applicant, null, 8, 8, 8, InterviewResult.PASS, null, LocalDateTime.now()
        );
        evaluation.setId(id);
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
