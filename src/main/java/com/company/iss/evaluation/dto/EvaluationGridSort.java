package com.company.iss.evaluation.dto;

import java.util.Arrays;
import java.util.Optional;

public enum EvaluationGridSort {
    APPLICANT("applicant", "applicant.lastName"),
    POSITION("position", "applicant.positionOpening.title"),
    CLIENT("client", "applicant.positionOpening.client.companyName"),
    STAGE("stage", "booking.interviewStage"),
    RESULT("result", "result"),
    EVALUATOR("evaluator", "evaluator.fullName"),
    EVALUATION_DATE("evaluationDate", "evaluationDate");

    private final String key;
    private final String property;

    EvaluationGridSort(String key, String property) {
        this.key = key;
        this.property = property;
    }

    public String key() {
        return key;
    }

    public String property() {
        return property;
    }

    public static Optional<EvaluationGridSort> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
