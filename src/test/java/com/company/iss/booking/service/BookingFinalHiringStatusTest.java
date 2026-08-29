package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingFinalHiringStatusTest {

    @Mock NotificationService notificationService;
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
        org.mockito.Mockito.lenient()
                .when(securityService.requireOperationsUser(any(String.class)))
                .thenAnswer(invocation -> securityService.getCurrentUser());
        service = new BookingService(
                notificationService,
                bookingRepository,
                historyRepository,
                evaluationRepository,
                scheduleRepository,
                applicantService,
                securityService,
                eventPublisher
        );
    }

    @ParameterizedTest
    @EnumSource(value = ApplicantStatus.class, names = {"OFFERED", "HIRED", "OFFER_DECLINED", "WITHDRAWN"})
    void finalHiringStatusesCannotBeBooked(ApplicantStatus status) {
        Branch branch = new Branch();
        branch.setId(1L);
        User actor = new User();
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        Applicant applicant = new Applicant();
        applicant.setId(10L);
        applicant.setBranch(branch);
        applicant.setActive(true);
        applicant.setStatus(status);
        Schedule schedule = new Schedule();
        schedule.setId(20L);
        schedule.setBranch(branch);
        schedule.setActive(true);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setBookedCount(0);
        schedule.setSlotCapacity(1);

        when(securityService.getCurrentUser()).thenReturn(actor);
        when(applicantService.findForBookingUpdate(10L, actor)).thenReturn(applicant);
        when(scheduleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(schedule));

        assertThrows(BusinessRuleViolationException.class, () -> service.createBooking(10L, 20L, null));

        verify(scheduleRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(applicantService, never()).updateStatus(any(), any());
    }
}
