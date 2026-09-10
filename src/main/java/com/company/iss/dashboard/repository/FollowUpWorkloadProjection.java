package com.company.iss.dashboard.repository;

public interface FollowUpWorkloadProjection {

    Long getTotal();

    Long getOnTrack();

    Long getDueSoon();

    Long getOverdue();

    Long getTimingUnavailable();
}
