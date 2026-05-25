package com.company.iss.dashboard.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.dashboard.dto.DashboardMetrics;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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

        metrics.setOpenPositions(positionOpeningRepository.countActiveOpenings());

        metrics.setTodaysInterviews(scheduleRepository.countByScheduleDate(LocalDate.now()));

        metrics.setBookedInterviews(bookingRepository.countByStatus(BookingStatus.BOOKED));

        metrics.setPassedApplicants(applicantRepository.countByStatus(ApplicantStatus.PASSED));

        metrics.setFailedApplicants(applicantRepository.countByStatus(ApplicantStatus.FAILED));

        metrics.setNoShows(bookingRepository.countByStatus(BookingStatus.NO_SHOW));

        return metrics;
    }
}