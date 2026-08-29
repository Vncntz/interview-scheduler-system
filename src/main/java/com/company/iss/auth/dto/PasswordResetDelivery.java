package com.company.iss.auth.dto;

public record PasswordResetDelivery(
        String recipientEmail,
        String userName,
        String resetLink,
        long expiresInMinutes
) {
}
