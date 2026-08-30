package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingRescheduleHistory;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface BookingRescheduleHistoryRepository
        extends Repository<BookingRescheduleHistory, Long>, BookingRescheduleHistoryAppender {

    List<BookingRescheduleHistory> findByBookingIdOrderByRescheduledAtAscIdAsc(Long bookingId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "booking", "sourceSchedule", "sourceSchedule.recruiter",
            "destinationSchedule", "destinationSchedule.recruiter", "actor"
    })
    List<BookingRescheduleHistory> findByBookingApplicantIdOrderByRescheduledAtAscIdAsc(Long applicantId);
}
