package com.company.iss.dashboard.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.branch.entity.Branch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterWorkbenchServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock SecurityService securityService;

    private RecruiterWorkbenchService service;

    @BeforeEach
    void setUp() {
        service = new RecruiterWorkbenchService(bookingRepository, securityService);
    }

    @Test
    void everyBranchQueueUsesTheAuthenticatedRecruitersBranch() {
        Branch branch = new Branch();
        branch.setId(17L);
        User recruiter = new User();
        recruiter.setId(23L);
        recruiter.setRole(Role.RECRUITER);
        recruiter.setActive(true);
        recruiter.setBranch(branch);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(bookingRepository.findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
                eq(23L), any(LocalDate.class), any()
        )).thenReturn(List.of());
        when(bookingRepository.findUpcomingAssigned(eq(23L), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                17L, BookingStatus.BOOKED
        )).thenReturn(List.of());
        when(bookingRepository.findDueByBranchAndStatus(eq(17L), eq(BookingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(bookingRepository.findOverdueUnevaluatedByBranch(eq(17L), eq(BookingStatus.ATTENDED), any(), any()))
                .thenReturn(List.of());

        service.load();

        verify(bookingRepository).findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                17L, BookingStatus.BOOKED
        );
        verify(bookingRepository).findDueByBranchAndStatus(
                eq(17L), eq(BookingStatus.CONFIRMED), any(LocalDate.class), any(LocalTime.class)
        );
        verify(bookingRepository).findOverdueUnevaluatedByBranch(
                eq(17L), eq(BookingStatus.ATTENDED), any(LocalDate.class), any(LocalTime.class)
        );
    }
}
