package com.company.iss.booking.repository;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRescheduleHistoryRepository extends JpaRepository<BookingRescheduleHistory, Long> {

    List<BookingRescheduleHistory> findByBookingOrderByRescheduledAtAsc(Booking booking);
}
