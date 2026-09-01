package com.company.iss.schedule.dto;

import java.util.Arrays;
import java.util.Optional;

public enum ScheduleGridSort {
    DATE("date", "scheduleDate"),
    START_TIME("startTime", "startTime"),
    END_TIME("endTime", "endTime"),
    BRANCH("branch", "branch.branchName"),
    RECRUITER("recruiter", "recruiter.fullName"),
    MODE("mode", "interviewMode"),
    CAPACITY("capacity", "slotCapacity"),
    BOOKED("booked", "bookedCount"),
    STATUS("status", "status");

    private final String key;
    private final String property;

    ScheduleGridSort(String key, String property) {
        this.key = key;
        this.property = property;
    }

    public String key() {
        return key;
    }

    public String property() {
        return property;
    }

    public static Optional<ScheduleGridSort> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
