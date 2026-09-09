package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingLifecycleHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface BookingLifecycleHistoryRepository
        extends Repository<BookingLifecycleHistory, Long>, BookingLifecycleHistoryAppender {

    List<BookingLifecycleHistory> findByBookingIdOrderByOccurredAtAscIdAsc(Long bookingId);

    @EntityGraph(attributePaths = {"booking"})
    List<BookingLifecycleHistory> findByBookingApplicantIdOrderByOccurredAtAscIdAsc(Long applicantId);
}
