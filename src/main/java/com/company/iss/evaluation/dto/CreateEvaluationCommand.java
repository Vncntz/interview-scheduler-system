package com.company.iss.evaluation.dto;

import com.company.iss.evaluation.entity.InterviewResult;

public record CreateEvaluationCommand(
        Long bookingId,
        Integer communicationScore,
        Integer technicalScore,
        Integer attitudeScore,
        InterviewResult result,
        String remarks
) {
}
