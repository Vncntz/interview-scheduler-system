package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.branch.entity.Branch;
import com.company.iss.position.entity.PositionOpening;

public interface ApplicantAssignmentGuard {

    void validateReassignment(Applicant applicant, Branch requestedBranch, PositionOpening requestedPosition);
}
