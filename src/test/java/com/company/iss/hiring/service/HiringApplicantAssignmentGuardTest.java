package com.company.iss.hiring.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.branch.entity.Branch;
import com.company.iss.hiring.repository.HiringDecisionRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HiringApplicantAssignmentGuardTest {

    @Test
    void decisionProtectsBranchAndPositionWhileAllowingNonAssignmentEdits() {
        HiringDecisionRepository repository = mock(HiringDecisionRepository.class);
        when(repository.existsByApplicantId(10L)).thenReturn(true);
        HiringApplicantAssignmentGuard guard = new HiringApplicantAssignmentGuard(repository);
        Applicant applicant = applicant(10L, 1L, 2L);

        assertDoesNotThrow(() -> guard.validateReassignment(applicant, branch(1L), position(2L)));
        assertThrows(
                BusinessRuleViolationException.class,
                () -> guard.validateReassignment(applicant, branch(3L), position(2L))
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> guard.validateReassignment(applicant, branch(1L), position(4L))
        );
    }

    private Applicant applicant(Long id, Long branchId, Long positionId) {
        Applicant applicant = new Applicant();
        applicant.setId(id);
        applicant.setBranch(branch(branchId));
        applicant.setPositionOpening(position(positionId));
        return applicant;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    private PositionOpening position(Long id) {
        PositionOpening position = new PositionOpening();
        position.setId(id);
        return position;
    }
}
