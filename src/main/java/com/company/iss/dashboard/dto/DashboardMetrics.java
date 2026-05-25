package com.company.iss.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardMetrics {

    private Long totalApplicants;
    private Long openPositions;
    private Long todaysInterviews;
    private Long bookedInterviews;
    private Long passedApplicants;
    private Long failedApplicants;
    private Long noShows;

}