package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final PositionOpeningRepository positionOpeningRepository;
    private final BranchRepository branchRepository;
    private final BookingRepository bookingRepository;
    private final SecurityService securityService;

    public ApplicantService(
            ApplicantRepository applicantRepository,
            PositionOpeningRepository positionOpeningRepository,
            BranchRepository branchRepository,
            BookingRepository bookingRepository,
            SecurityService securityService
    ) {
        this.applicantRepository = applicantRepository;
        this.positionOpeningRepository = positionOpeningRepository;
        this.branchRepository = branchRepository;
        this.bookingRepository = bookingRepository;
        this.securityService = securityService;
    }

    @Transactional
    public Applicant save(Applicant input) {
        if (input == null) {
            throw new IllegalArgumentException("Applicant is required.");
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
    public List<Applicant> search(String keyword) {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() == Role.ADMIN) {
            if (keyword == null || keyword.isBlank()) {
                return applicantRepository.findAll();
            }
            return applicantRepository
                    .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            keyword, keyword, keyword
                    );
        }

        Long branchId = actor.getBranch().getId();
        if (keyword == null || keyword.isBlank()) {
            return applicantRepository.findByBranchIdOrderByLastNameAscFirstNameAsc(branchId);
        }
        return applicantRepository
                .findByBranchIdAndFirstNameContainingIgnoreCaseOrBranchIdAndLastNameContainingIgnoreCaseOrBranchIdAndEmailContainingIgnoreCase(
                        branchId, keyword, branchId, keyword, branchId, keyword
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
            throw new IllegalArgumentException("Applicant is required.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return applicantRepository.findByIdForUpdate(applicantId)
                    .orElseThrow(() -> new IllegalArgumentException("Applicant not found."));
        }
        return applicantRepository.findByIdAndBranchIdForUpdate(applicantId, actor.getBranch().getId())
                .orElseThrow(() -> new AccessDeniedException("You may only manage applicants within your branch."));
    }

    private PositionOpening requirePosition(Applicant input) {
        if (input.getPositionOpening() == null || input.getPositionOpening().getId() == null) {
            throw new IllegalArgumentException("Position opening is required.");
        }
        return positionOpeningRepository.findById(input.getPositionOpening().getId())
                .orElseThrow(() -> new IllegalArgumentException("Position opening not found."));
    }

    private void validateRequiredFields(Applicant input) {
        if (input.getFirstName() == null || input.getFirstName().isBlank()) {
            throw new IllegalArgumentException("First name is required.");
        }
        if (input.getLastName() == null || input.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required.");
        }
        if (input.getMobileNumber() == null || input.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile number is required.");
        }
    }

    private Branch resolveBranch(User actor, Branch requestedBranch) {
        if (actor.getRole() == Role.RECRUITER) {
            return actor.getBranch();
        }
        if (requestedBranch == null || requestedBranch.getId() == null) {
            throw new IllegalArgumentException("Branch is required.");
        }
        return branchRepository.findById(requestedBranch.getId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
    }

    private void validateEmailAvailable(String email, Long currentApplicantId) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        applicantRepository.findByEmail(email).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), currentApplicantId)) {
                throw new IllegalArgumentException("Applicant email already exists.");
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
            throw new IllegalStateException(
                    "An applicant with an active booking cannot be reassigned to another branch."
            );
        }
    }
}
