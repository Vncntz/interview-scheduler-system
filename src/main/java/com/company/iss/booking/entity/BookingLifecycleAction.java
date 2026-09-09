package com.company.iss.booking.entity;

public enum BookingLifecycleAction {
    BOOKING_CREATED,
    BOOKING_CONFIRMED,
    ATTENDANCE_RECORDED,
    NO_SHOW_RECORDED,
    BOOKING_CANCELLED;

    void validateTransition(BookingStatus previousStatus, BookingStatus newStatus) {
        boolean valid = switch (this) {
            case BOOKING_CREATED -> previousStatus == null && newStatus == BookingStatus.BOOKED;
            case BOOKING_CONFIRMED -> previousStatus == BookingStatus.BOOKED
                    && newStatus == BookingStatus.CONFIRMED;
            case ATTENDANCE_RECORDED -> previousStatus == BookingStatus.CONFIRMED
                    && newStatus == BookingStatus.ATTENDED;
            case NO_SHOW_RECORDED -> previousStatus == BookingStatus.CONFIRMED
                    && newStatus == BookingStatus.NO_SHOW;
            case BOOKING_CANCELLED -> (previousStatus == BookingStatus.BOOKED
                    || previousStatus == BookingStatus.CONFIRMED)
                    && newStatus == BookingStatus.CANCELLED;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Invalid booking lifecycle transition for " + this
                            + ": " + previousStatus + " -> " + newStatus
            );
        }
    }
}
