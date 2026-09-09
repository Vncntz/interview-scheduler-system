package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingLifecycleHistory;

public interface BookingLifecycleHistoryAppender {

    BookingLifecycleHistory append(BookingLifecycleHistory history);
}
