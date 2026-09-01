package com.company.iss.schedule.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.schedule.dto.ScheduleGridFilter;
import com.company.iss.schedule.dto.ScheduleGridSort;
import com.company.iss.schedule.dto.ScheduleGridSortOrder;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleGridServiceTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock BranchRepository branchRepository;
    @Mock UserRepository userRepository;
    @Mock SecurityService securityService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(scheduleRepository, branchRepository, userRepository, securityService);
    }

    @Test
    void forwardsExactWindowDerivedEnumMatchesAndTypedSort() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        when(scheduleRepository.findGridPage(
                any(), any(Boolean.class), any(Boolean.class), any(Boolean.class),
                any(Boolean.class), any(Boolean.class), any(Boolean.class), any(Boolean.class), any(Pageable.class)
        )).thenReturn(List.of());

        service.findGridPage(
                new ScheduleGridFilter("  ON  "), 25, 10,
                List.of(new ScheduleGridSortOrder(ScheduleGridSort.RECRUITER, Sort.Direction.DESC))
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleRepository).findGridPage(
                eq("%on%"), eq(true), eq(true), eq(true),
                eq(false), eq(false), eq(false), eq(false), pageable.capture()
        );
        assertInstanceOf(OffsetLimitPageable.class, pageable.getValue());
        assertEquals(25, pageable.getValue().getOffset());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(
                List.of(new Sort.Order(Sort.Direction.DESC, "recruiter.fullName"), Sort.Order.asc("id")),
                pageable.getValue().getSort().toList()
        );
    }

    @Test
    void blankFilterUsesDefaultStableSortAndCountParityCriteria() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        when(scheduleRepository.findGridPage(
                any(), any(Boolean.class), any(Boolean.class), any(Boolean.class),
                any(Boolean.class), any(Boolean.class), any(Boolean.class), any(Boolean.class), any(Pageable.class)
        )).thenReturn(List.of());

        service.findGridPage(new ScheduleGridFilter("  "), 0, 50, List.of());
        service.countGrid(ScheduleGridFilter.empty());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleRepository).findGridPage(
                eq(null), eq(false), eq(false), eq(false),
                eq(false), eq(false), eq(false), eq(false), pageable.capture()
        );
        assertEquals(
                List.of(Sort.Order.asc("scheduleDate"), Sort.Order.asc("startTime"), Sort.Order.asc("id")),
                pageable.getValue().getSort().toList()
        );
        verify(scheduleRepository).countGrid(null, false, false, false, false, false, false, false);
    }

    @Test
    void rejectsInvalidWindowsAndNonAdminBeforeRepositoryAccess() {
        when(securityService.requireOperationsUser()).thenReturn(admin());
        assertThrows(IllegalArgumentException.class,
                () -> service.findGridPage(ScheduleGridFilter.empty(), -1, 50, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.findGridPage(ScheduleGridFilter.empty(), 0, 101, List.of()));
        verify(scheduleRepository, never()).findGridPage(any(), any(Boolean.class), any(Boolean.class),
                any(Boolean.class), any(Boolean.class), any(Boolean.class), any(Boolean.class),
                any(Boolean.class), any());

        when(securityService.requireOperationsUser()).thenReturn(recruiter());
        assertThrows(AccessDeniedException.class, () -> service.countGrid(ScheduleGridFilter.empty()));
    }

    private User admin() {
        User user = new User();
        user.setRole(Role.ADMIN);
        return user;
    }

    private User recruiter() {
        User user = new User();
        user.setRole(Role.RECRUITER);
        return user;
    }
}
