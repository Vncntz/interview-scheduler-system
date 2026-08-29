package com.company.iss.booking.dto;

import com.company.iss.booking.entity.BookingStatus;

import java.time.LocalDate;
import java.util.Locale;

public record BookingGridFilter(String keyword, BookingStatus status, LocalDate scheduleDate) {

    public BookingGridFilter {
        keyword = normalizeKeyword(keyword);
    }

    public static BookingGridFilter empty() {
        return new BookingGridFilter(null, null, null);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }
}
