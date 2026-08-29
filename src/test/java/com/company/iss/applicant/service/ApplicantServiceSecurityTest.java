package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.dto.ApplicantGridFilter;
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
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceSecurityTest {

    @Mock ApplicantRepository applicantRepository;
    @Mock PositionOpeningRepository positionOpeningRepository;
    @Mock BranchRepository branchRepository;
    @Mock BookingRepository bookingRepository;
    @Mock SecurityService securityService;
    @Mock ApplicantAssignmentGuard applicantAssignmentGuard;

    private ApplicantService service;

    @BeforeEach
    void setUp() {
        service = new ApplicantService(
                applicantRepository,
                positionOpeningRepository,
                branchRepository,
                bookingRepository,
                securityService,
                applicantAssignmentGuard
        );
    }

    @Test
    void recruiterListUsesOnlyTheActorsBranchQuery() {
        User recruiter = recruiter(12L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findGridPage(12L, null, null, PageRequest.of(0, 50))).thenReturn(List.of());

        assertTrue(service.findGridPage(null, 0, 50).isEmpty());

        verify(applicantRepository).findGridPage(12L, null, null, PageRequest.of(0, 50));
        verify(applicantRepository, never()).findAll();
    }

    @Test
    void adminGridPageUsesGlobalNormalizedFilterAndBoundedPageRequest() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(applicantRepository.findGridPage(
                null, "alex", ApplicantStatus.SCREENING, PageRequest.of(2, 25)
        )).thenReturn(List.of());

        service.findGridPage(new ApplicantGridFilter("  ALEX  ", ApplicantStatus.SCREENING), 2, 25);

        verify(applicantRepository).findGridPage(
                null, "alex", ApplicantStatus.SCREENING, PageRequest.of(2, 25)
        );
    }

    @Test
    void recruiterCountUsesTheActorsApplicantBranchScope() {
        User recruiter = recruiter(12L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.countGrid(12L, "candidate", ApplicantStatus.NEW)).thenReturn(7L);

        long count = service.countGrid(new ApplicantGridFilter(" candidate ", ApplicantStatus.NEW));

        assertEquals(7L, count);
        verify(applicantRepository).countGrid(12L, "candidate", ApplicantStatus.NEW);
    }

    @Test
    void rejectsInvalidGridBoundsBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, 0, 101));

        verify(applicantRepository, never()).findGridPage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void guessedApplicantIdFromAnotherBranchIsDeniedWithoutWrite() {
        User recruiter = recruiter(12L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findByIdAndBranchIdForUpdate(99L, 12L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.deactivate(99L));

        verify(applicantRepository, never()).save(org.mockito.ArgumentMatchers.any(Applicant.class));
    }

    @Test
    void adminCannotReassignApplicantBranchWhileActiveBookingExists() {
        Branch existingBranch = branch(12L);
        Branch requestedBranch = branch(13L);
        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setActive(true);

        PositionOpening position = new PositionOpening();
        position.setId(30L);
        Applicant existing = validApplicant(99L, existingBranch, position);
        Applicant input = validApplicant(99L, requestedBranch, position);

        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(positionOpeningRepository.findById(30L)).thenReturn(Optional.of(position));
        when(branchRepository.findById(13L)).thenReturn(Optional.of(requestedBranch));
        when(applicantRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(existing));
        when(applicantRepository.findByEmail(input.getEmail())).thenReturn(Optional.of(existing));
        when(bookingRepository.existsByApplicantIdAndStatusIn(
                99L, List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED))).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> service.save(input));

        assertSame(existingBranch, existing.getBranch());
        verify(applicantRepository, never()).save(org.mockito.ArgumentMatchers.any(Applicant.class));
        verify(positionOpeningRepository, never()).save(org.mockito.ArgumentMatchers.any(PositionOpening.class));
    }

    @Test
    void adminCannotCreateApplicantWithoutExplicitBranch() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        PositionOpening position = new PositionOpening();
        position.setId(30L);
        Applicant input = validApplicant(null, null, position);

        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(positionOpeningRepository.findById(30L)).thenReturn(Optional.of(position));

        BusinessRuleViolationException error = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.save(input)
        );

        assertTrue(error.getMessage().contains("Branch is required"));
        verify(applicantRepository, never()).save(org.mockito.ArgumentMatchers.any(Applicant.class));
        verify(positionOpeningRepository, never()).save(org.mockito.ArgumentMatchers.any(PositionOpening.class));
    }

    private User recruiter(Long branchId) {
        Branch branch = branch(branchId);
        User user = new User();
        user.setRole(Role.RECRUITER);
        user.setActive(true);
        user.setBranch(branch);
        return user;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    private Applicant validApplicant(Long id, Branch branch, PositionOpening position) {
        Applicant applicant = new Applicant();
        applicant.setId(id);
        applicant.setFirstName("Alex");
        applicant.setLastName("Candidate");
        applicant.setEmail("alex@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        return applicant;
    }
}
