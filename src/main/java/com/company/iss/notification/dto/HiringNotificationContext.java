package com.company.iss.notification.dto;

public record HiringNotificationContext(
        String recipientEmail,
        String applicantName,
        String position,
        String client,
        String workLocation
) {
}
