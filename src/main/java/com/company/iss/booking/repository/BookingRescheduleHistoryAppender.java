package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingRescheduleHistory;

public interface BookingRescheduleHistoryAppender {

    BookingRescheduleHistory append(BookingRescheduleHistory history);
}
