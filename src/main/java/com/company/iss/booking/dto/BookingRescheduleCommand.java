package com.company.iss.booking.dto;

public record BookingRescheduleCommand(
        Long bookingId,
        Long destinationScheduleId,
        String reason
) {
}
