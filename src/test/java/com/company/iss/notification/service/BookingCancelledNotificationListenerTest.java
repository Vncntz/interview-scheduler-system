package com.company.iss.notification.service;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.event.BookingCancelledEvent;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.notification.entity.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingCancelledNotificationListenerTest {

    @Test
    void loadsCommittedBookingAndSendsCancellationNotification() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Booking booking = new Booking();
        when(bookingRepository.findDetailedById(42L)).thenReturn(Optional.of(booking));

        BookingCancelledNotificationListener listener =
                new BookingCancelledNotificationListener(bookingRepository, notificationService);

        listener.onBookingCancelled(new BookingCancelledEvent(42L));

        verify(notificationService).send(NotificationEvent.BOOKING_CANCELLED, booking);
    }

    @Test
    void missingBookingSkipsNotification() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        when(bookingRepository.findDetailedById(42L)).thenReturn(Optional.empty());

        BookingCancelledNotificationListener listener =
                new BookingCancelledNotificationListener(bookingRepository, notificationService);

        listener.onBookingCancelled(new BookingCancelledEvent(42L));

        verify(notificationService, never()).send(eq(NotificationEvent.BOOKING_CANCELLED), any(Booking.class));
    }

    @Test
    void notificationFailureIsContained() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Booking booking = new Booking();
        when(bookingRepository.findDetailedById(42L)).thenReturn(Optional.of(booking));
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
                .when(notificationService)
                .send(NotificationEvent.BOOKING_CANCELLED, booking);

        BookingCancelledNotificationListener listener =
                new BookingCancelledNotificationListener(bookingRepository, notificationService);

        listener.onBookingCancelled(new BookingCancelledEvent(42L));

        verify(notificationService).send(NotificationEvent.BOOKING_CANCELLED, booking);
    }

    @Test
    void listenerIsAsynchronousAfterCommitAndUsesNewReadOnlyTransaction() throws Exception {
        Method method = BookingCancelledNotificationListener.class.getMethod(
                "onBookingCancelled",
                BookingCancelledEvent.class
        );

        assertNotNull(method.getAnnotation(Async.class));
        TransactionalEventListener eventListener = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(eventListener);
        assertEquals(TransactionPhase.AFTER_COMMIT, eventListener.phase());
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertTrue(transactional.readOnly());
    }
}
