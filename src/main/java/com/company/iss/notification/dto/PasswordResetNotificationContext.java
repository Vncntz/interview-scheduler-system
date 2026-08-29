package com.company.iss.notification.dto;

public record PasswordResetNotificationContext(
        String recipientEmail,
        String userName,
        String resetLink,
        long expiresInMinutes
) {
}
