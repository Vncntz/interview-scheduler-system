package com.company.iss.dashboard.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.dashboard.dto.RecruiterWorkbenchData;
import com.company.iss.dashboard.dto.WorkbenchInterview;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class RecruiterWorkbenchService {

    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED
    );
    private static final List<BookingStatus> TODAY_STATUSES = List.of(
            BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.ATTENDED
    );

    private final BookingRepository bookingRepository;
    private final SecurityService securityService;

    public RecruiterWorkbenchService(BookingRepository bookingRepository, SecurityService securityService) {
        this.bookingRepository = bookingRepository;
        this.securityService = securityService;
    }

    @Transactional(readOnly = true)
    public RecruiterWorkbenchData load() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException("The recruiter workbench is only available to recruiters.");
        }

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        Long branchId = actor.getBranch().getId();

        return new RecruiterWorkbenchData(
                map(bookingRepository.findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
                        actor.getId(), today, TODAY_STATUSES
                )),
                map(bookingRepository.findUpcomingAssigned(actor.getId(), today, now, ACTIVE_STATUSES)),
                map(bookingRepository.findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
                        branchId, BookingStatus.BOOKED
                )),
                map(bookingRepository.findDueByBranchAndStatus(
                        branchId, BookingStatus.CONFIRMED, today, now
                )),
                map(bookingRepository.findOverdueUnevaluatedByBranch(
                        branchId, BookingStatus.ATTENDED, today, now
                ))
        );
    }

    private List<WorkbenchInterview> map(List<Booking> bookings) {
        return bookings.stream().map(this::toDto).toList();
    }

    private WorkbenchInterview toDto(Booking booking) {
        return new WorkbenchInterview(
                booking.getId(),
                booking.getBookingReference(),
                booking.getApplicant().getFullName(),
                booking.getApplicant().getPositionOpening() == null
                        ? "Unassigned"
                        : booking.getApplicant().getPositionOpening().getTitle(),
                booking.getSchedule().getScheduleDate(),
                booking.getSchedule().getStartTime(),
                booking.getSchedule().getEndTime(),
                booking.getSchedule().getRecruiter().getFullName(),
                booking.getStatus()
        );
    }
}
