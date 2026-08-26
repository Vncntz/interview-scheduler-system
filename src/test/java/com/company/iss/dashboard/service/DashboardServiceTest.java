package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.dashboard.dto.DashboardMetrics;
import com.company.iss.dashboard.dto.InterviewActivity;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private PositionOpeningRepository positionOpeningRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void metricsUseOpenPositionsAndActiveNonCancelledSchedules() {
        when(applicantRepository.count()).thenReturn(20L);
        when(positionOpeningRepository.countByActiveTrueAndStatus(PositionStatus.OPEN)).thenReturn(4L);
        when(scheduleRepository.countByScheduleDateAndActiveTrueAndStatusNot(
                any(LocalDate.class),
                eq(ScheduleStatus.CANCELLED)
        )).thenReturn(3L);
        when(bookingRepository.countByStatus(BookingStatus.BOOKED)).thenReturn(5L);
        when(applicantRepository.countByStatus(ApplicantStatus.PASSED)).thenReturn(6L);
        when(applicantRepository.countByStatus(ApplicantStatus.FAILED)).thenReturn(2L);
        when(bookingRepository.countByStatus(BookingStatus.NO_SHOW)).thenReturn(1L);

        DashboardMetrics metrics = dashboardService.getMetrics();

        assertEquals(20L, metrics.getTotalApplicants());
        assertEquals(4L, metrics.getOpenPositions());
        assertEquals(3L, metrics.getTodaysInterviews());
        assertEquals(5L, metrics.getBookedInterviews());
        assertEquals(6L, metrics.getPassedApplicants());
        assertEquals(2L, metrics.getFailedApplicants());
        assertEquals(1L, metrics.getNoShows());
    }

    @Test
    void interviewActivityReturnsSevenConsecutiveDaysIncludingZeroCounts() {
        when(scheduleRepository.findByScheduleDateBetweenAndActiveTrueAndStatusNot(
                any(LocalDate.class),
                any(LocalDate.class),
                eq(ScheduleStatus.CANCELLED)
        )).thenAnswer(invocation -> {
            LocalDate startDate = invocation.getArgument(0);
            return List.of(
                    scheduleOn(startDate.plusDays(1)),
                    scheduleOn(startDate.plusDays(2)),
                    scheduleOn(startDate.plusDays(2))
            );
        });

        List<InterviewActivity> activity = dashboardService.getInterviewActivity();

        assertEquals(7, activity.size());
        LocalDate startDate = activity.getFirst().date();
        for (int day = 0; day < activity.size(); day++) {
            assertEquals(startDate.plusDays(day), activity.get(day).date());
        }
        assertEquals(List.of(0L, 1L, 2L, 0L, 0L, 0L, 0L), activity.stream()
                .map(InterviewActivity::scheduledSchedules)
                .toList());
    }

    @Test
    void upcomingInterviewsAreLimitedToFiveAtTheRepositoryBoundary() {
        when(bookingRepository.findUpcoming(
                any(LocalDate.class),
                any(LocalTime.class),
                anyList(),
                eq(ScheduleStatus.CANCELLED),
                any(Pageable.class)
        )).thenReturn(List.of());

        assertTrue(dashboardService.getUpcomingInterviews().isEmpty());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findUpcoming(
                any(LocalDate.class),
                any(LocalTime.class),
                anyList(),
                eq(ScheduleStatus.CANCELLED),
                pageable.capture()
        );
        assertEquals(5, pageable.getValue().getPageSize());
    }

    private Schedule scheduleOn(LocalDate date) {
        Schedule schedule = new Schedule();
        schedule.setScheduleDate(date);
        return schedule;
    }
}
