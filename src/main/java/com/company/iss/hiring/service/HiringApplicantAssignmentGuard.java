package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.service.ApplicantAssignmentGuard;
import com.company.iss.branch.entity.Branch;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.hiring.exception.HiringDecisionException;
import com.company.iss.position.entity.PositionOpening;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class HiringApplicantAssignmentGuard implements ApplicantAssignmentGuard {

    private final HiringDecisionRepository hiringDecisionRepository;

    public HiringApplicantAssignmentGuard(HiringDecisionRepository hiringDecisionRepository) {
        this.hiringDecisionRepository = hiringDecisionRepository;
    }

    @Override
    public void validateReassignment(Applicant applicant, Branch requestedBranch, PositionOpening requestedPosition) {
        if (applicant == null || applicant.getId() == null || !hiringDecisionRepository.existsByApplicantId(applicant.getId())) {
            return;
        }
        Long existingBranchId = applicant.getBranch() == null ? null : applicant.getBranch().getId();
        Long requestedBranchId = requestedBranch == null ? null : requestedBranch.getId();
        Long existingPositionId = applicant.getPositionOpening() == null ? null : applicant.getPositionOpening().getId();
        Long requestedPositionId = requestedPosition == null ? null : requestedPosition.getId();
        if (!Objects.equals(existingBranchId, requestedBranchId)
                || !Objects.equals(existingPositionId, requestedPositionId)) {
            throw new HiringDecisionException(
                    "An applicant with a hiring decision cannot be reassigned to another branch or position."
            );
        }
    }
}
