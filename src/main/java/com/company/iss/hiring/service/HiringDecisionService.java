package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.HiringDecisionAuditSummary;
import com.company.iss.hiring.dto.HiringDecisionSummary;
import com.company.iss.hiring.dto.IssueOfferCommand;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class HiringDecisionService {

    private static final List<HiringDecisionStatus> TERMINAL_STATUSES = List.of(
            HiringDecisionStatus.HIRED,
            HiringDecisionStatus.DECLINED,
            HiringDecisionStatus.WITHDRAWN
    );

    private final HiringDecisionRepository decisionRepository;
    private final HiringDecisionAuditRepository auditRepository;
    private final ApplicantRepository applicantRepository;
    private final InterviewEvaluationRepository evaluationRepository;
    private final PositionOpeningRepository positionRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    public HiringDecisionService(
            HiringDecisionRepository decisionRepository,
            HiringDecisionAuditRepository auditRepository,
            ApplicantRepository applicantRepository,
            InterviewEvaluationRepository evaluationRepository,
            PositionOpeningRepository positionRepository,
            SecurityService securityService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
        this.applicantRepository = applicantRepository;
        this.evaluationRepository = evaluationRepository;
        this.positionRepository = positionRepository;
        this.securityService = securityService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<EligibleHiringCandidate> findEligibleCandidates() {
        User actor = securityService.requireOperationsUser();
        List<InterviewEvaluation> evaluations = actor.getRole() == Role.ADMIN
                ? decisionRepository.findEligibleEvaluations()
                : decisionRepository.findEligibleEvaluationsByBranchId(actor.getBranch().getId());
        return evaluations.stream().map(this::toEligibleCandidate).toList();
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionSummary> findOutstandingDecisions() {
        User actor = securityService.requireOperationsUser();
        List<HiringDecision> decisions = actor.getRole() == Role.ADMIN
                ? decisionRepository.findByStatusOrderByOfferedAtDesc(HiringDecisionStatus.OFFERED)
                : decisionRepository.findByStatusAndApplicantBranchIdOrderByOfferedAtDesc(
                        HiringDecisionStatus.OFFERED,
                        actor.getBranch().getId()
                );
        return decisions.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionSummary> findCompletedDecisions() {
        User actor = securityService.requireOperationsUser();
        List<HiringDecision> decisions = actor.getRole() == Role.ADMIN
                ? decisionRepository.findByStatusInOrderByResolvedAtDesc(TERMINAL_STATUSES)
                : decisionRepository.findByStatusInAndApplicantBranchIdOrderByResolvedAtDesc(
                        TERMINAL_STATUSES,
                        actor.getBranch().getId()
                );
        return decisions.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionAuditSummary> findAudit(Long decisionId) {
        User actor = securityService.requireOperationsUser();
        HiringDecision decision = requireDetailedDecision(decisionId);
        authorize(actor, decision.getApplicant());
        return auditRepository.findByDecisionIdOrderByOccurredAtAsc(decisionId).stream()
                .map(audit -> new HiringDecisionAuditSummary(
                        audit.getAction(),
                        audit.getPreviousStatus(),
                        audit.getNewStatus(),
                        audit.getActor().getFullName(),
                        audit.getOccurredAt(),
                        audit.getRemarks()
                ))
                .toList();
    }

    @Transactional
    public HiringDecisionSummary issueOffer(IssueOfferCommand command) {
        validateIssueCommand(command);
        User actor = securityService.requireOperationsUser();
        Applicant applicant = requireScopedApplicantForUpdate(command.applicantId(), actor);

        HiringDecision existing = decisionRepository.findByApplicantIdForUpdate(applicant.getId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == HiringDecisionStatus.OFFERED
                    && Objects.equals(existing.getEvaluation().getId(), command.evaluationId())) {
                return toSummary(existing);
            }
            throw new HiringDecisionException(
                    "This applicant already has a hiring decision and cannot receive another offer."
            );
        }

        InterviewEvaluation evaluation = evaluationRepository.findDetailedById(command.evaluationId())
                .orElseThrow(() -> new HiringDecisionException("Interview evaluation not found."));
        validateEligible(applicant, evaluation);

        LocalDateTime now = LocalDateTime.now();
        HiringDecision decision = new HiringDecision();
        decision.setApplicant(applicant);
        decision.setEvaluation(evaluation);
        decision.setPosition(applicant.getPositionOpening());
        decision.setStatus(HiringDecisionStatus.OFFERED);
        decision.setOfferedBy(actor);
        decision.setOfferedAt(now);
        decision.setOfferedRemarks(trimToNull(command.remarks()));
        decision = decisionRepository.saveAndFlush(decision);

        applicant.setStatus(ApplicantStatus.OFFERED);
        applicantRepository.save(applicant);
        appendAudit(
                decision,
                HiringDecisionAction.OFFER_ISSUED,
                null,
                HiringDecisionStatus.OFFERED,
                actor,
                now,
                command.remarks()
        );
        eventPublisher.publishEvent(new JobOfferIssuedEvent(decision.getId()));
        return toSummary(decision);
    }

    @Transactional
    public HiringDecisionSummary acceptAndHire(HiringActionCommand command) {
        validateActionCommand(command, false);
        User actor = securityService.requireOperationsUser();
        Applicant applicant = requireScopedApplicantForUpdate(command.applicantId(), actor);
        HiringDecision decision = requireDecisionForUpdate(applicant.getId());
        authorize(actor, decision.getApplicant());

        if (decision.getStatus() == HiringDecisionStatus.HIRED) {
            return toSummary(decision);
        }
        requireOutstanding(decision, HiringDecisionAction.ACCEPTED_AND_HIRED);

        PositionOpening position = positionRepository.findByIdForUpdate(decision.getPosition().getId())
                .orElseThrow(() -> new HiringDecisionException("Position opening not found."));
        validateHireCapacity(decision, applicant, position);

        int hiredCount = position.getHiredCount() + 1;
        position.setHiredCount(hiredCount);
        if (hiredCount >= position.getRequiredHeadcount()) {
            position.setStatus(PositionStatus.FILLED);
        }

        LocalDateTime now = LocalDateTime.now();
        completeDecision(decision, HiringDecisionStatus.HIRED, applicant, ApplicantStatus.HIRED, actor, now, command.remarks());
        positionRepository.save(position);
        appendAudit(
                decision,
                HiringDecisionAction.ACCEPTED_AND_HIRED,
                HiringDecisionStatus.OFFERED,
                HiringDecisionStatus.HIRED,
                actor,
                now,
                command.remarks()
        );
        eventPublisher.publishEvent(new ApplicantHiredEvent(decision.getId()));
        return toSummary(decision);
    }

    @Transactional
    public HiringDecisionSummary decline(HiringActionCommand command) {
        return resolveWithoutHire(
                command,
                HiringDecisionStatus.DECLINED,
                ApplicantStatus.OFFER_DECLINED,
                HiringDecisionAction.DECLINED
        );
    }

    @Transactional
    public HiringDecisionSummary withdraw(HiringActionCommand command) {
        return resolveWithoutHire(
                command,
                HiringDecisionStatus.WITHDRAWN,
                ApplicantStatus.WITHDRAWN,
                HiringDecisionAction.WITHDRAWN
        );
    }

    private HiringDecisionSummary resolveWithoutHire(
            HiringActionCommand command,
            HiringDecisionStatus targetStatus,
            ApplicantStatus applicantStatus,
            HiringDecisionAction action
    ) {
        validateActionCommand(command, false);
        User actor = securityService.requireOperationsUser();
        Applicant applicant = requireScopedApplicantForUpdate(command.applicantId(), actor);
        HiringDecision decision = requireDecisionForUpdate(applicant.getId());
        authorize(actor, decision.getApplicant());

        if (decision.getStatus() == targetStatus) {
            return toSummary(decision);
        }
        requireOutstanding(decision, action);
        requireReason(command.remarks());
        validateOutstandingApplicantState(decision, applicant);

        LocalDateTime now = LocalDateTime.now();
        completeDecision(decision, targetStatus, applicant, applicantStatus, actor, now, command.remarks());
        appendAudit(
                decision,
                action,
                HiringDecisionStatus.OFFERED,
                targetStatus,
                actor,
                now,
                command.remarks()
        );
        return toSummary(decision);
    }

    private void completeDecision(
            HiringDecision decision,
            HiringDecisionStatus decisionStatus,
            Applicant applicant,
            ApplicantStatus applicantStatus,
            User actor,
            LocalDateTime resolvedAt,
            String remarks
    ) {
        decision.setStatus(decisionStatus);
        decision.setResolvedBy(actor);
        decision.setResolvedAt(resolvedAt);
        decision.setResolutionRemarks(trimToNull(remarks));
        applicant.setStatus(applicantStatus);
        applicantRepository.save(applicant);
        decisionRepository.save(decision);
    }

    private void validateEligible(Applicant applicant, InterviewEvaluation evaluation) {
        Booking booking = evaluation.getBooking();
        PositionOpening position = applicant.getPositionOpening();
        if (!applicant.isActive() || applicant.getStatus() != ApplicantStatus.PASSED) {
            throw new HiringDecisionException("Only active applicants with a passed result are eligible for an offer.");
        }
        if (evaluation.getResult() != InterviewResult.PASS || booking == null || booking.getStatus() != BookingStatus.PASSED) {
            throw new HiringDecisionException("The selected evaluation and booking must both be passed.");
        }
        if (evaluation.getApplicant() == null || booking.getApplicant() == null
                || !Objects.equals(evaluation.getApplicant().getId(), applicant.getId())
                || !Objects.equals(booking.getApplicant().getId(), applicant.getId())) {
            throw new HiringDecisionException("The evaluation, booking, and applicant do not match.");
        }
        if (position == null || evaluation.getApplicant().getPositionOpening() == null
                || !Objects.equals(position.getId(), evaluation.getApplicant().getPositionOpening().getId())) {
            throw new HiringDecisionException("The evaluation does not match the applicant's position.");
        }
        if (!position.isActive() || position.getStatus() != PositionStatus.OPEN
                || position.getHiredCount() == null || position.getRequiredHeadcount() == null
                || position.getHiredCount() >= position.getRequiredHeadcount()) {
            throw new HiringDecisionException("The position is not open or has no remaining headcount.");
        }
    }

    private void validateHireCapacity(HiringDecision decision, Applicant applicant, PositionOpening position) {
        validateOutstandingApplicantState(decision, applicant);
        if (applicant.getPositionOpening() == null
                || decision.getEvaluation().getApplicant() == null
                || !Objects.equals(decision.getApplicant().getId(), applicant.getId())
                || !Objects.equals(decision.getPosition().getId(), applicant.getPositionOpening().getId())
                || !Objects.equals(decision.getEvaluation().getApplicant().getId(), applicant.getId())) {
            throw new HiringDecisionException("The hiring decision no longer matches the applicant and position.");
        }
        if (!position.isActive() || position.getStatus() != PositionStatus.OPEN
                || position.getHiredCount() == null || position.getRequiredHeadcount() == null
                || position.getHiredCount() >= position.getRequiredHeadcount()) {
            throw new HiringDecisionException("The position is not open or has no remaining headcount.");
        }
    }

    private void validateOutstandingApplicantState(HiringDecision decision, Applicant applicant) {
        Booking booking = decision.getEvaluation().getBooking();
        if (!applicant.isActive()
                || applicant.getStatus() != ApplicantStatus.OFFERED
                || decision.getEvaluation().getResult() != InterviewResult.PASS
                || booking == null
                || booking.getStatus() != BookingStatus.PASSED) {
            throw new HiringDecisionException(
                    "The outstanding offer is no longer consistent with the applicant's passed interview."
            );
        }
    }

    private Applicant requireScopedApplicantForUpdate(Long applicantId, User actor) {
        if (applicantId == null) {
            throw new HiringDecisionException("Applicant is required.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return applicantRepository.findByIdForUpdate(applicantId)
                    .orElseThrow(() -> new HiringDecisionException("Applicant not found."));
        }
        return applicantRepository.findByIdAndBranchIdForUpdate(applicantId, actor.getBranch().getId())
                .orElseThrow(() -> new AccessDeniedException("You may only manage applicants within your branch."));
    }

    private HiringDecision requireDecisionForUpdate(Long applicantId) {
        return decisionRepository.findByApplicantIdForUpdate(applicantId)
                .orElseThrow(() -> new HiringDecisionException("Hiring decision not found."));
    }

    private HiringDecision requireDetailedDecision(Long decisionId) {
        if (decisionId == null) {
            throw new HiringDecisionException("Hiring decision is required.");
        }
        return decisionRepository.findDetailedById(decisionId)
                .orElseThrow(() -> new HiringDecisionException("Hiring decision not found."));
    }

    private void requireOutstanding(HiringDecision decision, HiringDecisionAction attemptedAction) {
        if (decision.getStatus() != HiringDecisionStatus.OFFERED) {
            throw new HiringDecisionException(
                    "A " + decision.getStatus().name().toLowerCase() + " decision cannot be changed by "
                            + attemptedAction.name().toLowerCase() + "."
            );
        }
    }

    private void authorize(User actor, Applicant applicant) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (applicant.getBranch() == null
                || !Objects.equals(actor.getBranch().getId(), applicant.getBranch().getId())) {
            throw new AccessDeniedException("You may only manage hiring decisions within your branch.");
        }
    }

    private void appendAudit(
            HiringDecision decision,
            HiringDecisionAction action,
            HiringDecisionStatus previousStatus,
            HiringDecisionStatus newStatus,
            User actor,
            LocalDateTime occurredAt,
            String remarks
    ) {
        auditRepository.append(HiringDecisionAudit.record(
                decision,
                action,
                previousStatus,
                newStatus,
                actor,
                occurredAt,
                trimToNull(remarks)
        ));
    }

    private void validateIssueCommand(IssueOfferCommand command) {
        if (command == null || command.applicantId() == null || command.evaluationId() == null) {
            throw new HiringDecisionException("Applicant and evaluation are required.");
        }
        validateRemarksLength(command.remarks());
    }

    private void validateActionCommand(HiringActionCommand command, boolean reasonRequired) {
        if (command == null || command.applicantId() == null) {
            throw new HiringDecisionException("Applicant is required.");
        }
        if (reasonRequired && (command.remarks() == null || command.remarks().isBlank())) {
            throw new HiringDecisionException("A reason is required.");
        }
        validateRemarksLength(command.remarks());
    }

    private void requireReason(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            throw new HiringDecisionException("A reason is required.");
        }
    }

    private void validateRemarksLength(String remarks) {
        if (remarks != null && remarks.trim().length() > 1000) {
            throw new HiringDecisionException("Remarks must not exceed 1000 characters.");
        }
    }

    private EligibleHiringCandidate toEligibleCandidate(InterviewEvaluation evaluation) {
        Applicant applicant = evaluation.getApplicant();
        PositionOpening position = applicant.getPositionOpening();
        return new EligibleHiringCandidate(
                applicant.getId(),
                evaluation.getId(),
                applicant.getFullName(),
                applicant.getBranch().getBranchName(),
                position.getTitle(),
                position.getClient() == null ? "" : position.getClient().getCompanyName(),
                position.getWorkLocation(),
                evaluation.getEvaluationDate()
        );
    }

    private HiringDecisionSummary toSummary(HiringDecision decision) {
        Applicant applicant = decision.getApplicant();
        PositionOpening position = decision.getPosition();
        return new HiringDecisionSummary(
                decision.getId(),
                applicant.getId(),
                applicant.getFullName(),
                applicant.getBranch().getBranchName(),
                position.getTitle(),
                position.getClient() == null ? "" : position.getClient().getCompanyName(),
                position.getWorkLocation(),
                decision.getStatus(),
                decision.getOfferedBy().getFullName(),
                decision.getOfferedAt(),
                decision.getOfferedRemarks(),
                decision.getResolvedBy() == null ? "" : decision.getResolvedBy().getFullName(),
                decision.getResolvedAt(),
                decision.getResolutionRemarks()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
