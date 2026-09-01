package com.company.iss.evaluation.dto;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;

import java.time.LocalDate;
import java.util.Locale;

public record EvaluationGridFilter(
        String keyword,
        InterviewStage interviewStage,
        InterviewResult result,
        LocalDate evaluationDate
) {

    public EvaluationGridFilter {
        keyword = normalize(keyword);
    }

    public static EvaluationGridFilter empty() {
        return new EvaluationGridFilter(null, null, null, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
