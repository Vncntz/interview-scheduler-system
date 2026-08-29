package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.dto.ApplicantGridFilter;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Objects;

@Service
public class ApplicantService {

    private static final int MAX_GRID_PAGE_SIZE = 100;

    private final ApplicantRepository applicantRepository;
    private final PositionOpeningRepository positionOpeningRepository;
    private final BranchRepository branchRepository;
    private final BookingRepository bookingRepository;
    private final SecurityService securityService;
    private final ApplicantAssignmentGuard applicantAssignmentGuard;

    public ApplicantService(
            ApplicantRepository applicantRepository,
            PositionOpeningRepository positionOpeningRepository,
            BranchRepository branchRepository,
            BookingRepository bookingRepository,
            SecurityService securityService,
            ApplicantAssignmentGuard applicantAssignmentGuard
    ) {
        this.applicantRepository = applicantRepository;
        this.positionOpeningRepository = positionOpeningRepository;
        this.branchRepository = branchRepository;
        this.bookingRepository = bookingRepository;
        this.securityService = securityService;
        this.applicantAssignmentGuard = applicantAssignmentGuard;
    }

    @Transactional
    public Applicant save(Applicant input) {
        if (input == null) {
            throw new BusinessRuleViolationException("Applicant is required.");
        }
        validateRequiredFields(input);

        User actor = securityService.requireOperationsUser();
        PositionOpening position = requirePosition(input);
        Branch branch = resolveBranch(actor, input.getBranch());

        if (input.getId() == null) {
            validateEmailAvailable(input.getEmail(), null);
            input.setPositionOpening(position);
            input.setBranch(branch);
            input.setStatus(ApplicantStatus.NEW);
            input.setActive(true);
            position.setAppliedCount(position.getAppliedCount() + 1);
            positionOpeningRepository.save(position);
            return applicantRepository.save(input);
        }

        Applicant existing = findForUpdate(input.getId(), actor);
        validateEmailAvailable(input.getEmail(), existing.getId());
        validateBranchReassignment(existing, branch);
        applicantAssignmentGuard.validateReassignment(existing, branch, position);

        PositionOpening oldPosition = existing.getPositionOpening();
        if (oldPosition == null || !Objects.equals(oldPosition.getId(), position.getId())) {
            if (oldPosition != null && oldPosition.getAppliedCount() > 0) {
                oldPosition.setAppliedCount(oldPosition.getAppliedCount() - 1);
                positionOpeningRepository.save(oldPosition);
            }
            position.setAppliedCount(position.getAppliedCount() + 1);
            positionOpeningRepository.save(position);
        }

        existing.setFirstName(input.getFirstName());
        existing.setMiddleName(input.getMiddleName());
        existing.setLastName(input.getLastName());
        existing.setEmail(input.getEmail());
        existing.setMobileNumber(input.getMobileNumber());
        existing.setPositionOpening(position);
        existing.setBranch(branch);
        existing.setSource(input.getSource());
        existing.setRemarks(input.getRemarks());
        return applicantRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<Applicant> findGridPage(ApplicantGridFilter filter, int page, int pageSize) {
        validateGridPage(page, pageSize);
        User actor = securityService.requireOperationsUser();
        ApplicantGridFilter normalizedFilter = filter == null ? ApplicantGridFilter.empty() : filter;
        return applicantRepository.findGridPage(
                gridBranchId(actor),
                normalizedFilter.keyword(),
                normalizedFilter.status(),
                PageRequest.of(page, pageSize)
        );
    }

    @Transactional(readOnly = true)
    public long countGrid(ApplicantGridFilter filter) {
        User actor = securityService.requireOperationsUser();
        ApplicantGridFilter normalizedFilter = filter == null ? ApplicantGridFilter.empty() : filter;
        return applicantRepository.countGrid(
                gridBranchId(actor),
                normalizedFilter.keyword(),
                normalizedFilter.status()
        );
    }

    @Transactional
    public void activate(Long applicantId) {
        Applicant applicant = findForUpdate(applicantId, securityService.requireOperationsUser());
        applicant.setActive(true);
        applicantRepository.save(applicant);
    }

    @Transactional
    public void deactivate(Long applicantId) {
        Applicant applicant = findForUpdate(applicantId, securityService.requireOperationsUser());
        applicant.setActive(false);
        applicantRepository.save(applicant);
    }

    public void updateStatus(Applicant applicant, ApplicantStatus status) {
        applicant.setStatus(status);
        applicantRepository.save(applicant);
    }

    @Transactional
    public Applicant findForBookingUpdate(Long applicantId, User actor) {
        return findForUpdate(applicantId, actor);
    }

    private Applicant findForUpdate(Long applicantId, User actor) {
        if (applicantId == null) {
            throw new BusinessRuleViolationException("Applicant is required.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return applicantRepository.findByIdForUpdate(applicantId)
                    .orElseThrow(() -> new BusinessRuleViolationException("Applicant not found."));
        }
        return applicantRepository.findByIdAndBranchIdForUpdate(applicantId, actor.getBranch().getId())
                .orElseThrow(() -> new AccessDeniedException("You may only manage applicants within your branch."));
    }

    private Long gridBranchId(User actor) {
        return actor.getRole() == Role.ADMIN ? null : actor.getBranch().getId();
    }

    private void validateGridPage(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("Grid page must not be negative.");
        }
        if (pageSize < 1 || pageSize > MAX_GRID_PAGE_SIZE) {
            throw new IllegalArgumentException("Grid page size must be between 1 and 100.");
        }
    }

    private PositionOpening requirePosition(Applicant input) {
        if (input.getPositionOpening() == null || input.getPositionOpening().getId() == null) {
            throw new BusinessRuleViolationException("Position opening is required.");
        }
        return positionOpeningRepository.findById(input.getPositionOpening().getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Position opening not found."));
    }

    private void validateRequiredFields(Applicant input) {
        if (input.getFirstName() == null || input.getFirstName().isBlank()) {
            throw new BusinessRuleViolationException("First name is required.");
        }
        if (input.getLastName() == null || input.getLastName().isBlank()) {
            throw new BusinessRuleViolationException("Last name is required.");
        }
        if (input.getMobileNumber() == null || input.getMobileNumber().isBlank()) {
            throw new BusinessRuleViolationException("Mobile number is required.");
        }
    }

    private Branch resolveBranch(User actor, Branch requestedBranch) {
        if (actor.getRole() == Role.RECRUITER) {
            return actor.getBranch();
        }
        if (requestedBranch == null || requestedBranch.getId() == null) {
            throw new BusinessRuleViolationException("Branch is required.");
        }
        return branchRepository.findById(requestedBranch.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Branch not found."));
    }

    private void validateEmailAvailable(String email, Long currentApplicantId) {
        if (email == null || email.isBlank()) {
            throw new BusinessRuleViolationException("Email is required.");
        }
        applicantRepository.findByEmail(email).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), currentApplicantId)) {
                throw new BusinessRuleViolationException("Applicant email already exists.");
            }
        });
    }

    private void validateBranchReassignment(Applicant existing, Branch requestedBranch) {
        Long existingBranchId = existing.getBranch() == null ? null : existing.getBranch().getId();
        Long requestedBranchId = requestedBranch == null ? null : requestedBranch.getId();
        if (Objects.equals(existingBranchId, requestedBranchId)) {
            return;
        }
        if (bookingRepository.existsByApplicantIdAndStatusIn(
                existing.getId(), List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED))) {
            throw new BusinessRuleViolationException(
                    "An applicant with an active booking cannot be reassigned to another branch."
            );
        }
    }
}
