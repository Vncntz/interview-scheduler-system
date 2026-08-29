package com.company.iss.booking.service;

import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.dto.BookingGridFilter;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingGridServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingRescheduleHistoryRepository historyRepository;
    @Mock InterviewEvaluationRepository evaluationRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock ApplicantService applicantService;
    @Mock SecurityService securityService;
    @Mock ApplicationEventPublisher eventPublisher;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                bookingRepository,
                historyRepository,
                evaluationRepository,
                scheduleRepository,
                applicantService,
                securityService,
                eventPublisher
        );
    }

    @Test
    void recruiterGridFetchAndCountUseApplicantBranchScope() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        User recruiter = recruiter(44L);
        when(securityService.requireOperationsUser("You are not authorized to view bookings."))
                .thenReturn(recruiter);
        when(bookingRepository.findGridPage(
                44L, "alex candidate", BookingStatus.CONFIRMED, date, PageRequest.of(1, 50)
        )).thenReturn(List.of());
        when(bookingRepository.countGrid(44L, "alex candidate", BookingStatus.CONFIRMED, date))
                .thenReturn(3L);

        BookingGridFilter filter = new BookingGridFilter(
                "  Alex Candidate ", BookingStatus.CONFIRMED, date
        );

        service.findGridPage(filter, 1, 50);
        assertEquals(3L, service.countGrid(filter));

        verify(bookingRepository).findGridPage(
                44L, "alex candidate", BookingStatus.CONFIRMED, date, PageRequest.of(1, 50)
        );
        verify(bookingRepository).countGrid(44L, "alex candidate", BookingStatus.CONFIRMED, date);
    }

    @Test
    void adminNullFilterUsesGlobalEmptyFilter() {
        User admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        when(securityService.requireOperationsUser("You are not authorized to view bookings."))
                .thenReturn(admin);
        when(bookingRepository.findGridPage(null, null, null, null, PageRequest.of(0, 100)))
                .thenReturn(List.of());

        service.findGridPage(null, 0, 100);

        verify(bookingRepository).findGridPage(null, null, null, null, PageRequest.of(0, 100));
    }

    @Test
    void rejectsInvalidGridBoundsBeforeAuthorizationOrRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findGridPage(null, 0, 101));

        verify(securityService, never()).requireOperationsUser("You are not authorized to view bookings.");
        verify(bookingRepository, never()).findGridPage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private User recruiter(Long branchId) {
        Branch branch = new Branch();
        branch.setId(branchId);
        User recruiter = new User();
        recruiter.setRole(Role.RECRUITER);
        recruiter.setActive(true);
        recruiter.setBranch(branch);
        return recruiter;
    }
}
