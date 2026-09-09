package com.company.iss.booking.repository;

import com.company.iss.booking.entity.BookingLifecycleHistory;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class BookingLifecycleHistoryAppenderImpl implements BookingLifecycleHistoryAppender {

    private final EntityManager entityManager;

    public BookingLifecycleHistoryAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public BookingLifecycleHistory append(BookingLifecycleHistory history) {
        Objects.requireNonNull(history, "history is required");
        if (history.getId() != null) {
            throw new IllegalArgumentException("Persisted booking lifecycle history cannot be appended again.");
        }
        entityManager.persist(history);
        return history;
    }
}
