package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.event.BookingRescheduledEvent;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.entity.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingRescheduledNotificationListenerTest {

    @Test
    void loadsCommittedBookingAndSendsRescheduleNotification() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Booking booking = new Booking();
        when(bookingRepository.findDetailedById(42L)).thenReturn(Optional.of(booking));

        BookingRescheduledNotificationListener listener =
                new BookingRescheduledNotificationListener(bookingRepository, notificationService);

        listener.onBookingRescheduled(new BookingRescheduledEvent(42L));

        verify(notificationService).send(NotificationEvent.BOOKING_RESCHEDULED, booking);
    }

    @Test
    void listenerIsAsynchronousAndRunsOnlyAfterCommit() throws Exception {
        Method method = BookingRescheduledNotificationListener.class.getMethod(
                "onBookingRescheduled",
                BookingRescheduledEvent.class
        );

        assertNotNull(method.getAnnotation(Async.class));
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation);
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
