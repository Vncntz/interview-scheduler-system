package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.dashboard.dto.DashboardMetrics;
import com.company.iss.dashboard.dto.InterviewActivity;
import com.company.iss.dashboard.dto.ScheduleSummary;
import com.company.iss.dashboard.dto.UpcomingInterview;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DashboardService {

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PositionOpeningRepository positionOpeningRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    public DashboardMetrics getMetrics() {
        DashboardMetrics metrics = new DashboardMetrics();

        metrics.setTotalApplicants(applicantRepository.count());

        metrics.setOpenPositions(positionOpeningRepository.countByActiveTrueAndStatus(PositionStatus.OPEN));

        metrics.setTodaysInterviews(scheduleRepository.countByScheduleDateAndActiveTrueAndStatusNot(
                LocalDate.now(),
                ScheduleStatus.CANCELLED
        ));

        metrics.setBookedInterviews(bookingRepository.countByStatus(BookingStatus.BOOKED));

        metrics.setPassedApplicants(applicantRepository.countByStatus(ApplicantStatus.PASSED));

        metrics.setFailedApplicants(applicantRepository.countByStatus(ApplicantStatus.FAILED));

        metrics.setNoShows(bookingRepository.countByStatus(BookingStatus.NO_SHOW));

        return metrics;
    }

    @Transactional(readOnly = true)
    public List<UpcomingInterview> getUpcomingInterviews() {
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.BOOKED,
                BookingStatus.CONFIRMED,
                BookingStatus.RESCHEDULED,
                BookingStatus.FOR_FINAL_INTERVIEW,
                BookingStatus.FOR_CLIENT_INTERVIEW
        );

        return bookingRepository
                .findUpcoming(
                        LocalDate.now(),
                        LocalTime.now(),
                        activeStatuses,
                        ScheduleStatus.CANCELLED,
                        PageRequest.of(0, 5)
                )
                .stream()
                .map(booking -> new UpcomingInterview(
                        booking.getSchedule().getScheduleDate(),
                        booking.getSchedule().getStartTime(),
                        booking.getApplicant().getPositionOpening() == null
                                ? "Unassigned"
                                : booking.getApplicant().getPositionOpening().getTitle(),
                        booking.getApplicant().getFullName(),
                        booking.getSchedule().getRecruiter().getFullName(),
                        booking.getSchedule().getBranch().getBranchName(),
                        booking.getSchedule().getInterviewMode(),
                        booking.getStatus()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleSummary> getTodaysSchedule() {
        return scheduleRepository.findByScheduleDateAndActiveTrueAndStatusNot(
                        LocalDate.now(),
                        ScheduleStatus.CANCELLED
                )
                .stream()
                .sorted((left, right) -> left.getStartTime().compareTo(right.getStartTime()))
                .map(this::toScheduleSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewActivity> getInterviewActivity() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(6);

        Map<LocalDate, Long> schedulesByDate = scheduleRepository
                .findByScheduleDateBetweenAndActiveTrueAndStatusNot(
                        startDate,
                        endDate,
                        ScheduleStatus.CANCELLED
                )
                .stream()
                .map(Schedule::getScheduleDate)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return IntStream.rangeClosed(0, 6)
                .mapToObj(startDate::plusDays)
                .map(date -> new InterviewActivity(date, schedulesByDate.getOrDefault(date, 0L)))
                .toList();
    }

    private ScheduleSummary toScheduleSummary(Schedule schedule) {
        return new ScheduleSummary(
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getBookedCount(),
                schedule.getSlotCapacity(),
                schedule.getInterviewMode(),
                schedule.getStatus(),
                schedule.getBranch().getBranchName()
        );
    }
}
