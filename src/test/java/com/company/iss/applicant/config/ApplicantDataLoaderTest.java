package com.company.iss.applicant.config;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantDataLoaderTest {

    @Test
    void existingApplicantDataMakesLoaderIdempotent() {
        ApplicantRepository applicants = mock(ApplicantRepository.class);
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(applicants.count()).thenReturn(1L);

        new ApplicantDataLoader(applicants, positions, branches).run();

        verify(applicants, never()).save(any());
        verify(positions, never()).findAll();
        verify(branches, never()).findAll();
    }

    @Test
    void missingPositionsSafelySkipsApplicantCreation() {
        ApplicantRepository applicants = mock(ApplicantRepository.class);
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(applicants.count()).thenReturn(0L);
        when(positions.findAll()).thenReturn(List.of());
        when(branches.findAll()).thenReturn(List.of(branch(1L, "A")));

        new ApplicantDataLoader(applicants, positions, branches).run();

        verify(applicants, never()).save(any());
        verify(positions, never()).save(any());
    }

    @Test
    void missingBranchSafelySkipsApplicantCreation() {
        ApplicantRepository applicants = mock(ApplicantRepository.class);
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(applicants.count()).thenReturn(0L);
        when(positions.findAll()).thenReturn(List.of(opening(1L, "Crew")));
        when(branches.findAll()).thenReturn(List.of());

        new ApplicantDataLoader(applicants, positions, branches).run();

        verify(applicants, never()).save(any());
        verify(positions, never()).save(any());
    }

    @Test
    void sortedInputsAndFixedSeedProduceDeterministicApplicantsAndRelations() {
        List<String> first = seedApplicantSequence(
                List.of(opening(2L, "Zulu"), opening(1L, "Alpha")),
                List.of(branch(2L, "Z"), branch(1L, "A"))
        );
        List<String> second = seedApplicantSequence(
                List.of(opening(1L, "Alpha"), opening(2L, "Zulu")),
                List.of(branch(1L, "A"), branch(2L, "Z"))
        );

        assertEquals(first, second);
    }

    private List<String> seedApplicantSequence(List<PositionOpening> openingInput, List<Branch> branchInput) {
        ApplicantRepository applicants = mock(ApplicantRepository.class);
        PositionOpeningRepository positions = mock(PositionOpeningRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        when(applicants.count()).thenReturn(0L);
        when(positions.findAll()).thenReturn(openingInput);
        when(branches.findAll()).thenReturn(branchInput);

        new ApplicantDataLoader(applicants, positions, branches).run();

        ArgumentCaptor<Applicant> captor = ArgumentCaptor.forClass(Applicant.class);
        verify(applicants, org.mockito.Mockito.times(100)).save(captor.capture());
        verify(positions, org.mockito.Mockito.times(100)).save(any());
        return captor.getAllValues().stream()
                .map(applicant -> applicant.getEmail()
                        + "|" + applicant.getMobileNumber()
                        + "|" + applicant.getPositionOpening().getTitle()
                        + "|" + applicant.getBranch().getBranchCode())
                .toList();
    }

    private PositionOpening opening(Long id, String title) {
        PositionOpening opening = new PositionOpening();
        opening.setId(id);
        opening.setTitle(title);
        opening.setAppliedCount(0);
        return opening;
    }

    private Branch branch(Long id, String code) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setBranchCode(code);
        return branch;
    }
}
