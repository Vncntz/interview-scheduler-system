package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingRescheduleHistory;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class BookingRescheduleHistoryAppenderImpl implements BookingRescheduleHistoryAppender {

    private final EntityManager entityManager;

    public BookingRescheduleHistoryAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public BookingRescheduleHistory append(BookingRescheduleHistory history) {
        Objects.requireNonNull(history, "history is required");
        if (history.getId() != null) {
            throw new IllegalArgumentException("Persisted reschedule history cannot be appended again.");
        }
        entityManager.persist(history);
        return history;
    }
}
