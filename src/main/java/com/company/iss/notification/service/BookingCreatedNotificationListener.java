package com.company.iss.notification.service;

import com.company.iss.booking.event.BookingCreatedEvent;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.entity.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingCreatedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCreatedNotificationListener.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public BookingCreatedNotificationListener(
            BookingRepository bookingRepository,
            NotificationService notificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        try {
            bookingRepository.findDetailedById(event.bookingId()).ifPresentOrElse(
                    booking -> notificationService.send(NotificationEvent.BOOKING_CREATED, booking),
                    () -> log.warn(
                            "[NOTIFICATION] Booking-created notification skipped bookingId={} reason=BOOKING_NOT_FOUND",
                            event.bookingId()
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[NOTIFICATION] Booking-created notification failed bookingId={} reason=DELIVERY_FAILED exception={}",
                    event.bookingId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
