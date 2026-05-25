package com.company.iss.shared.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {

    private static final DateTimeFormatter TIME_12H = DateTimeFormatter.ofPattern("hh:mm a");

    private DateTimeUtil() {
    }

    public static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }

        return time.format(TIME_12H);
    }
}