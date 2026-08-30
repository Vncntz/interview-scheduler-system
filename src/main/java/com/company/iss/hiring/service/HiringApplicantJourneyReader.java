package com.company.iss.hiring.service;

import com.company.iss.applicant.service.ApplicantHiringEventType;
import com.company.iss.applicant.service.ApplicantHiringJourneyContribution;
import com.company.iss.applicant.service.ApplicantHiringJourneyEvent;
import com.company.iss.applicant.service.ApplicantHiringJourneyReader;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionAction;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import com.company.iss.hiring.repository.HiringDecisionAuditRepository;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HiringApplicantJourneyReader implements ApplicantHiringJourneyReader {

    private final HiringDecisionRepository decisionRepository;
    private final HiringDecisionAuditRepository auditRepository;

    public HiringApplicantJourneyReader(
            HiringDecisionRepository decisionRepository,
            HiringDecisionAuditRepository auditRepository
    ) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public ApplicantHiringJourneyContribution read(Long applicantId) {
        boolean eligible = decisionRepository.existsEligibleEvaluationByApplicantId(applicantId);
        HiringDecision decision = decisionRepository.findByApplicantId(applicantId).orElse(null);
        if (decision == null) {
            return new ApplicantHiringJourneyContribution(eligible, false, List.of());
        }

        List<ApplicantHiringJourneyEvent> events = auditRepository
                .findByDecisionIdOrderByOccurredAtAscIdAsc(decision.getId())
                .stream()
                .map(audit -> new ApplicantHiringJourneyEvent(
                        audit.getId(),
                        map(audit.getAction()),
                        audit.getOccurredAt(),
                        audit.getActor() == null ? "" : audit.getActor().getFullName(),
                        audit.getRemarks()
                ))
                .toList();
        return new ApplicantHiringJourneyContribution(
                eligible,
                decision.getStatus() == HiringDecisionStatus.OFFERED,
                events
        );
    }

    private ApplicantHiringEventType map(HiringDecisionAction action) {
        return switch (action) {
            case OFFER_ISSUED -> ApplicantHiringEventType.JOB_OFFERED;
            case ACCEPTED_AND_HIRED -> ApplicantHiringEventType.HIRED;
            case DECLINED -> ApplicantHiringEventType.OFFER_DECLINED;
            case WITHDRAWN -> ApplicantHiringEventType.WITHDRAWN;
        };
    }
}
