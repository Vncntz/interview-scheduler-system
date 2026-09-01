package com.company.iss.dashboard.dto;

import java.util.List;

public record RecruiterWorkbenchData(
        List<WorkbenchInterview> todaysAssigned,
        List<WorkbenchInterview> upcomingAssigned,
        List<WorkbenchInterview> pendingConfirmations,
        List<WorkbenchInterview> attendanceQueue,
        List<WorkbenchInterview> overdueEvaluations,
        List<FollowUpApplicant> finalInterviewFollowUps,
        List<FollowUpApplicant> clientInterviewFollowUps
) {
}
