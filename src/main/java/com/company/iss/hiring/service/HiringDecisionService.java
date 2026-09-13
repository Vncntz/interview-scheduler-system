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
import com.company.iss.hiring.dto.CompletedDecisionSortOrder;
import com.company.iss.hiring.dto.EligibleCandidateSortOrder;
import com.company.iss.hiring.dto.EligibleHiringCandidate;
import com.company.iss.hiring.dto.HiringActionCommand;
import com.company.iss.hiring.dto.HiringDecisionAuditSummary;
import com.company.iss.hiring.dto.HiringDecisionSummary;
import com.company.iss.hiring.dto.HiringWorklistFilter;
import com.company.iss.hiring.dto.IssueOfferCommand;
import com.company.iss.hiring.dto.OutstandingDecisionSortOrder;
import com.company.iss.hiring.dto.OutstandingOfferFilter;
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
import com.company.iss.shared.pagination.OffsetLimitPageable;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class HiringDecisionService {

    private static final int MAX_WORKLIST_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
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
    private final OfferDeadlinePolicy deadlinePolicy;

    public HiringDecisionService(
            HiringDecisionRepository decisionRepository,
            HiringDecisionAuditRepository auditRepository,
            ApplicantRepository applicantRepository,
            InterviewEvaluationRepository evaluationRepository,
            PositionOpeningRepository positionRepository,
            SecurityService securityService,
            ApplicationEventPublisher eventPublisher,
            OfferDeadlinePolicy deadlinePolicy
    ) {
        this.decisionRepository = decisionRepository;
        this.auditRepository = auditRepository;
        this.applicantRepository = applicantRepository;
        this.evaluationRepository = evaluationRepository;
        this.positionRepository = positionRepository;
        this.securityService = securityService;
        this.eventPublisher = eventPublisher;
        this.deadlinePolicy = deadlinePolicy;
    }

    @Transactional(readOnly = true)
    public List<EligibleHiringCandidate> findEligiblePage(
            HiringWorklistFilter filter,
            long offset,
            int limit,
            List<EligibleCandidateSortOrder> sortOrders
    ) {
        User actor = securityService.requireOperationsUser();
        validateWorklistWindow(offset, limit);
        String keyword = normalizedKeyword(filter);
        return decisionRepository.findEligibleEvaluationPage(
                worklistBranchId(actor),
                keyword,
                new OffsetLimitPageable(offset, limit, eligibleSort(sortOrders))
        ).stream().map(this::toEligibleCandidate).toList();
    }

    @Transactional(readOnly = true)
    public long countEligible(HiringWorklistFilter filter) {
        User actor = securityService.requireOperationsUser();
        return decisionRepository.countEligibleEvaluationPage(worklistBranchId(actor), normalizedKeyword(filter));
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionSummary> findOutstandingPage(
            OutstandingOfferFilter filter,
            long offset,
            int limit,
            List<OutstandingDecisionSortOrder> sortOrders
    ) {
        User actor = securityService.requireOperationsUser();
        validateWorklistWindow(offset, limit);
        String keyword = normalizedKeyword(filter);
        StatusSearch statusSearch = statusSearch(keyword);
        LocalDateTime now = deadlinePolicy.now();
        LocalDateTime dueSoonCutoff = deadlinePolicy.dueSoonCutoff(now);
        OutstandingOfferFilter normalizedFilter = normalizedOutstandingFilter(filter);
        boolean offeredStatusMatches = statusSearch.matches()
                && statusSearch.statuses().contains(HiringDecisionStatus.OFFERED);
        List<HiringDecision> decisions = sortOrders == null || sortOrders.isEmpty()
                ? decisionRepository.findOutstandingDecisionPageByDeadlinePriority(
                        worklistBranchId(actor), keyword, offeredStatusMatches, normalizedFilter.deadline().name(),
                        now, dueSoonCutoff, new OffsetLimitPageable(offset, limit, Sort.unsorted()))
                : decisionRepository.findOutstandingDecisionPage(
                        worklistBranchId(actor), keyword, offeredStatusMatches, normalizedFilter.deadline().name(),
                        now, dueSoonCutoff, new OffsetLimitPageable(offset, limit, outstandingSort(sortOrders)));
        return decisions.stream().map(decision -> toSummary(decision, now)).toList();
    }

    @Transactional(readOnly = true)
    public long countOutstanding(OutstandingOfferFilter filter) {
        User actor = securityService.requireOperationsUser();
        String keyword = normalizedKeyword(filter);
        StatusSearch statusSearch = statusSearch(keyword);
        LocalDateTime now = deadlinePolicy.now();
        boolean offeredStatusMatches = statusSearch.matches()
                && statusSearch.statuses().contains(HiringDecisionStatus.OFFERED);
        return decisionRepository.countOutstandingDecisions(
                worklistBranchId(actor), keyword, offeredStatusMatches,
                normalizedOutstandingFilter(filter).deadline().name(),
                now, deadlinePolicy.dueSoonCutoff(now)
        );
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionSummary> findCompletedPage(
            HiringWorklistFilter filter,
            long offset,
            int limit,
            List<CompletedDecisionSortOrder> sortOrders
    ) {
        User actor = securityService.requireOperationsUser();
        validateWorklistWindow(offset, limit);
        String keyword = normalizedKeyword(filter);
        StatusSearch statusSearch = statusSearch(keyword);
        LocalDateTime now = deadlinePolicy.now();
        return decisionRepository.findDecisionPage(
                worklistBranchId(actor), TERMINAL_STATUSES, keyword,
                statusSearch.matches(), statusSearch.statuses(),
                new OffsetLimitPageable(offset, limit, completedSort(sortOrders))
        ).stream().map(decision -> toSummary(decision, now)).toList();
    }

    @Transactional(readOnly = true)
    public long countCompleted(HiringWorklistFilter filter) {
        User actor = securityService.requireOperationsUser();
        String keyword = normalizedKeyword(filter);
        StatusSearch statusSearch = statusSearch(keyword);
        return decisionRepository.countDecisionPage(
                worklistBranchId(actor), TERMINAL_STATUSES, keyword,
                statusSearch.matches(), statusSearch.statuses()
        );
    }

    @Transactional(readOnly = true)
    public List<HiringDecisionAuditSummary> findAudit(Long decisionId) {
        User actor = securityService.requireOperationsUser();
        HiringDecision decision = requireDetailedDecision(decisionId);
        authorize(actor, decision.getApplicant());
        return auditRepository.findByDecisionIdOrderByOccurredAtAscIdAsc(decisionId).stream()
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
        LocalDateTime responseDueAt = deadlinePolicy.normalize(command.responseDueAt());
        User actor = securityService.requireOperationsUser();
        Applicant applicant = requireScopedApplicantForUpdate(command.applicantId(), actor);

        HiringDecision existing = decisionRepository.findByApplicantIdForUpdate(applicant.getId()).orElse(null);
        if (existing != null) {
            boolean sameOutstandingOffer = existing.getStatus() == HiringDecisionStatus.OFFERED
                    && Objects.equals(existing.getEvaluation().getId(), command.evaluationId());
            if (sameOutstandingOffer) {
                if (Objects.equals(existing.getResponseDueAt(), responseDueAt)) {
                    return toSummary(existing);
                }
                validateResponseDeadline(responseDueAt, deadlinePolicy.normalize(deadlinePolicy.now()));
            }
            throw new HiringDecisionException(
                    "This applicant already has a hiring decision and cannot receive another offer."
            );
        }

        LocalDateTime now = deadlinePolicy.now();
        validateResponseDeadline(responseDueAt, deadlinePolicy.normalize(now));

        InterviewEvaluation evaluation = evaluationRepository.findDetailedById(command.evaluationId())
                .orElseThrow(() -> new HiringDecisionException("Interview evaluation not found."));
        validateEligible(applicant, evaluation);

        HiringDecision decision = new HiringDecision();
        decision.setApplicant(applicant);
        decision.setEvaluation(evaluation);
        decision.setPosition(applicant.getPositionOpening());
        decision.setStatus(HiringDecisionStatus.OFFERED);
        decision.setOfferedBy(actor);
        decision.setOfferedAt(now);
        decision.setResponseDueAt(responseDueAt);
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

        LocalDateTime now = deadlinePolicy.now();
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

        LocalDateTime now = deadlinePolicy.now();
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

    private void validateResponseDeadline(LocalDateTime responseDueAt, LocalDateTime now) {
        if (!deadlinePolicy.isFuture(responseDueAt, now)) {
            throw new HiringDecisionException("Response deadline must be in the future.");
        }
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

    private void validateWorklistWindow(long offset, int limit) {
        if (offset < 0 || offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Worklist offset must be between 0 and " + Integer.MAX_VALUE + ".");
        }
        if (limit < 1 || limit > MAX_WORKLIST_PAGE_SIZE) {
            throw new IllegalArgumentException("Worklist limit must be between 1 and " + MAX_WORKLIST_PAGE_SIZE + ".");
        }
    }

    private String normalizedKeyword(HiringWorklistFilter filter) {
        String keyword = filter == null ? null : trimToNull(filter.keyword());
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Search text must not exceed " + MAX_KEYWORD_LENGTH + " characters."
            );
        }
        return keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
    }

    private String normalizedKeyword(OutstandingOfferFilter filter) {
        return normalizedKeyword(new HiringWorklistFilter(filter == null ? null : filter.keyword()));
    }

    private OutstandingOfferFilter normalizedOutstandingFilter(OutstandingOfferFilter filter) {
        return filter == null ? OutstandingOfferFilter.empty() : filter;
    }

    private Long worklistBranchId(User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return null;
        }
        if (actor.getRole() != Role.RECRUITER || actor.getBranch() == null || actor.getBranch().getId() == null) {
            throw new AccessDeniedException("You may only view hiring decisions within your branch.");
        }
        return actor.getBranch().getId();
    }

    private StatusSearch statusSearch(String keyword) {
        if (keyword == null) {
            return new StatusSearch(false, List.of(HiringDecisionStatus.OFFERED));
        }
        List<HiringDecisionStatus> statuses = Arrays.stream(HiringDecisionStatus.values())
                .filter(status -> status.name().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
        return statuses.isEmpty()
                ? new StatusSearch(false, List.of(HiringDecisionStatus.OFFERED))
                : new StatusSearch(true, statuses);
    }

    private Sort eligibleSort(List<EligibleCandidateSortOrder> sortOrders) {
        if (sortOrders == null || sortOrders.isEmpty()) {
            return Sort.by(Sort.Order.desc("evaluationDate"), Sort.Order.desc("id"));
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (EligibleCandidateSortOrder order : sortOrders) {
            if (order == null) {
                throw new IllegalArgumentException("Eligible candidate sort order is required.");
            }
            order.field().properties().forEach(property -> orders.add(new Sort.Order(order.direction(), property)));
        }
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private Sort outstandingSort(List<OutstandingDecisionSortOrder> sortOrders) {
        List<Sort.Order> orders = new ArrayList<>();
        for (OutstandingDecisionSortOrder order : sortOrders) {
            if (order == null) {
                throw new IllegalArgumentException("Outstanding decision sort order is required.");
            }
            order.field().properties().forEach(property -> orders.add(new Sort.Order(order.direction(), property)));
        }
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private Sort completedSort(List<CompletedDecisionSortOrder> sortOrders) {
        if (sortOrders == null || sortOrders.isEmpty()) {
            return Sort.by(Sort.Order.desc("resolvedAt"), Sort.Order.desc("id"));
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (CompletedDecisionSortOrder order : sortOrders) {
            if (order == null) {
                throw new IllegalArgumentException("Completed decision sort order is required.");
            }
            order.field().properties().forEach(property -> orders.add(new Sort.Order(order.direction(), property)));
        }
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
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
        return toSummary(decision, deadlinePolicy.now());
    }

    private HiringDecisionSummary toSummary(HiringDecision decision, LocalDateTime now) {
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
                decision.getResponseDueAt(),
                deadlinePolicy.age(decision.getOfferedAt(), now),
                deadlinePolicy.classify(decision.getResponseDueAt(), now),
                decision.getOfferedRemarks(),
                decision.getResolvedBy() == null ? "" : decision.getResolvedBy().getFullName(),
                decision.getResolvedAt(),
                decision.getResolutionRemarks()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record StatusSearch(boolean matches, List<HiringDecisionStatus> statuses) {
    }
}
