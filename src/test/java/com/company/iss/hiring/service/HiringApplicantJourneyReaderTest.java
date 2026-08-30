package com.company.iss.hiring.service;

import com.company.iss.applicant.service.ApplicantHiringEventType;
import com.company.iss.auth.entity.User;
import com.company.iss.hiring.entity.*;
import com.company.iss.hiring.repository.HiringDecisionAuditRepository;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HiringApplicantJourneyReaderTest {

    @Test
    void mapsApplicantScopedEligibilityDecisionAndAllAuditActions() {
        HiringDecisionRepository decisions = mock(HiringDecisionRepository.class);
        HiringDecisionAuditRepository audits = mock(HiringDecisionAuditRepository.class);
        HiringDecision decision = new HiringDecision();
        decision.setId(12L);
        decision.setStatus(HiringDecisionStatus.OFFERED);
        User actor = new User();
        actor.setFullName("Admin User");
        List<HiringDecisionAudit> history = Arrays.stream(HiringDecisionAction.values())
                .map(action -> {
                    HiringDecisionAudit audit = HiringDecisionAudit.record(
                            decision, action, null,
                            action == HiringDecisionAction.ACCEPTED_AND_HIRED
                                    ? HiringDecisionStatus.HIRED : HiringDecisionStatus.OFFERED,
                            actor, LocalDateTime.of(2026, 8, 1, 8, 0).plusHours(action.ordinal()), "Remark"
                    );
                    audit.setId((long) action.ordinal() + 1);
                    return audit;
                }).toList();
        when(decisions.existsEligibleEvaluationByApplicantId(42L)).thenReturn(true);
        when(decisions.findByApplicantId(42L)).thenReturn(Optional.of(decision));
        when(audits.findByDecisionIdOrderByOccurredAtAscIdAsc(12L)).thenReturn(history);

        var contribution = new HiringApplicantJourneyReader(decisions, audits).read(42L);

        assertTrue(contribution.offerEligible());
        assertTrue(contribution.outstandingOffer());
        assertEquals(List.of(
                ApplicantHiringEventType.JOB_OFFERED,
                ApplicantHiringEventType.HIRED,
                ApplicantHiringEventType.OFFER_DECLINED,
                ApplicantHiringEventType.WITHDRAWN
        ), contribution.events().stream().map(event -> event.type()).toList());
        verify(decisions).existsEligibleEvaluationByApplicantId(42L);
        verify(decisions).findByApplicantId(42L);
        verify(audits).findByDecisionIdOrderByOccurredAtAscIdAsc(12L);
    }
}
