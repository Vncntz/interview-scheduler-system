package com.company.iss.evaluation.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.dto.EvaluationGridFilter;
import com.company.iss.evaluation.dto.EvaluationGridSort;
import com.company.iss.evaluation.dto.EvaluationGridSortOrder;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewEvaluationGridServiceTest {

    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock PositionOpeningRepository positionOpeningRepository;
    @Mock BookingRepository bookingRepository;
    @Mock SecurityService securityService;

    private InterviewEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new InterviewEvaluationService(
                evaluationRepository, positionOpeningRepository, bookingRepository, securityService
        );
    }

    @Test
    void adminUsesOrganizationScopeExactWindowFiltersAndDefaultSort() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        when(securityService.requireOperationsUser()).thenReturn(user(Role.ADMIN, null));
        when(evaluationRepository.findGridPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.findGridPage(
                new EvaluationGridFilter("  Alex  ", InterviewStage.FINAL, InterviewResult.PASS, date),
                25, 10, List.of()
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(evaluationRepository).findGridPage(
                eq(null), eq("%alex%"), eq(InterviewStage.FINAL), eq(InterviewResult.PASS),
                eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay()), pageable.capture()
        );
        assertEquals(25, pageable.getValue().getOffset());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(
                List.of(Sort.Order.desc("evaluationDate"), Sort.Order.desc("id")),
                pageable.getValue().getSort().toList()
        );
    }

    @Test
    void recruiterScopeUsesAuthoritativeActorBranchForFetchAndCount() {
        Branch branch = new Branch();
        branch.setId(42L);
        when(securityService.requireOperationsUser()).thenReturn(user(Role.RECRUITER, branch));
        when(evaluationRepository.findGridPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        EvaluationGridFilter filter = EvaluationGridFilter.empty();

        service.findGridPage(
                filter, 0, 50,
                List.of(new EvaluationGridSortOrder(EvaluationGridSort.APPLICANT, Sort.Direction.ASC))
        );
        service.countGrid(filter);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(evaluationRepository).findGridPage(
                eq(42L), eq(null), eq(null), eq(null), eq(null), eq(null), pageable.capture()
        );
        assertEquals(
                List.of(Sort.Order.asc("applicant.lastName"), Sort.Order.asc("id")),
                pageable.getValue().getSort().toList()
        );
        verify(evaluationRepository).countGrid(42L, null, null, null, null, null);
    }

    @Test
    void invalidWindowsAndMissingRecruiterBranchAreDenied() {
        when(securityService.requireOperationsUser()).thenReturn(user(Role.ADMIN, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.findGridPage(EvaluationGridFilter.empty(), -1, 50, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.findGridPage(EvaluationGridFilter.empty(), 0, 101, List.of()));
        verify(evaluationRepository, never()).findGridPage(any(), any(), any(), any(), any(), any(), any());

        when(securityService.requireOperationsUser()).thenReturn(user(Role.RECRUITER, null));
        assertThrows(AccessDeniedException.class, () -> service.countGrid(EvaluationGridFilter.empty()));

        when(securityService.requireOperationsUser()).thenReturn(user(Role.APPLICANT, null));
        assertThrows(AccessDeniedException.class,
                () -> service.findGridPage(EvaluationGridFilter.empty(), 0, 50, List.of()));
    }

    @Test
    void nullFilterIsNormalizedForAdminCount() {
        when(securityService.requireOperationsUser()).thenReturn(user(Role.ADMIN, null));

        service.countGrid(null);

        ArgumentCaptor<Long> branchId = ArgumentCaptor.forClass(Long.class);
        verify(evaluationRepository).countGrid(branchId.capture(), eq(null), eq(null), eq(null), eq(null), eq(null));
        assertNull(branchId.getValue());
    }

    private User user(Role role, Branch branch) {
        User user = new User();
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return user;
    }
}
