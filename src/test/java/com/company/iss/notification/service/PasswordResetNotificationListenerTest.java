package com.company.iss.notification.service;

import com.company.iss.auth.dto.PasswordResetDelivery;
import com.company.iss.auth.event.PasswordResetRequestedEvent;
import com.company.iss.auth.service.PasswordResetService;
import com.company.iss.notification.dto.PasswordResetNotificationContext;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetNotificationListenerTest {

    @Test
    void idOnlyEventReloadsDeliveryAfterCommit() {
        PasswordResetService resetService = mock(PasswordResetService.class);
        NotificationService notificationService = mock(NotificationService.class);
        PasswordResetDelivery delivery = new PasswordResetDelivery(
                "recruiter@example.test",
                "Recruiter User",
                "https://iss.example.test/reset-password?token=redacted-in-test",
                30
        );
        when(resetService.prepareDelivery(42L)).thenReturn(delivery);

        new PasswordResetNotificationListener(resetService, notificationService)
                .onPasswordResetRequested(new PasswordResetRequestedEvent(42L));

        verify(notificationService).sendPasswordReset(new PasswordResetNotificationContext(
                delivery.recipientEmail(), delivery.userName(), delivery.resetLink(), delivery.expiresInMinutes()
        ));
    }

    @Test
    void listenerIsAsynchronousAndAfterCommit() throws Exception {
        Method method = PasswordResetNotificationListener.class.getMethod(
                "onPasswordResetRequested", PasswordResetRequestedEvent.class
        );
        assertNotNull(method.getAnnotation(Async.class));
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation);
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
