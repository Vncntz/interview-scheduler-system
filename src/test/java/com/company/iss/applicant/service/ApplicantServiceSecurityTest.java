package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private ApplicantService service;

    @BeforeEach
    void setUp() {
        service = new ApplicantService(
                applicantRepository, positionOpeningRepository, branchRepository, bookingRepository, securityService
        );
    }

    @Test
    void recruiterListUsesOnlyTheActorsBranchQuery() {
        User recruiter = recruiter(12L);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(applicantRepository.findByBranchIdOrderByLastNameAscFirstNameAsc(12L)).thenReturn(List.of());

        assertTrue(service.search(null).isEmpty());

        verify(applicantRepository).findByBranchIdOrderByLastNameAscFirstNameAsc(12L);
        verify(applicantRepository, never()).findAll();
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

        assertThrows(IllegalStateException.class, () -> service.save(input));

        assertSame(existingBranch, existing.getBranch());
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
