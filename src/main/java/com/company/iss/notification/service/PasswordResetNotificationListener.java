package com.company.iss.notification.service;

import com.company.iss.auth.dto.PasswordResetDelivery;
import com.company.iss.auth.event.PasswordResetRequestedEvent;
import com.company.iss.auth.service.PasswordResetService;
import com.company.iss.notification.dto.PasswordResetNotificationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetNotificationListener {

    private final PasswordResetService passwordResetService;
    private final NotificationService notificationService;

    public PasswordResetNotificationListener(
            PasswordResetService passwordResetService,
            NotificationService notificationService
    ) {
        this.passwordResetService = passwordResetService;
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        PasswordResetDelivery delivery = passwordResetService.prepareDelivery(event.requestId());
        if (delivery == null) {
            return;
        }
        notificationService.sendPasswordReset(new PasswordResetNotificationContext(
                delivery.recipientEmail(),
                delivery.userName(),
                delivery.resetLink(),
                delivery.expiresInMinutes()
        ));
    }
}
