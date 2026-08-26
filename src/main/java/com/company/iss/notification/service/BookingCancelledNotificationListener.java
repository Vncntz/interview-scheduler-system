package com.company.iss.notification.service;

import com.company.iss.booking.event.BookingCancelledEvent;
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
public class BookingCancelledNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCancelledNotificationListener.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public BookingCancelledNotificationListener(
            BookingRepository bookingRepository,
            NotificationService notificationService
    ) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        try {
            bookingRepository.findDetailedById(event.bookingId()).ifPresentOrElse(
                    booking -> notificationService.send(NotificationEvent.BOOKING_CANCELLED, booking),
                    () -> log.warn(
                            "[NOTIFICATION] Cancellation notification skipped bookingId={} reason=BOOKING_NOT_FOUND",
                            event.bookingId()
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[NOTIFICATION] Cancellation notification failed bookingId={} exception={}",
                    event.bookingId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
