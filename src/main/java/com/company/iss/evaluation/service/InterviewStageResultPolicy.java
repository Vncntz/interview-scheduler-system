package com.company.iss.evaluation.service;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import java.util.List;

public class InterviewStageResultPolicy {

    public List<InterviewResult> allowedResults(InterviewStage stage) {
        if (stage == null) {
            throw new BusinessRuleViolationException("Interview stage is required before evaluation.");
        }
        return switch (stage) {
            case INITIAL -> List.of(
                    InterviewResult.PASS,
                    InterviewResult.FAIL,
                    InterviewResult.FOR_FINAL_INTERVIEW,
                    InterviewResult.FOR_CLIENT_INTERVIEW,
                    InterviewResult.ON_HOLD
            );
            case FINAL -> List.of(
                    InterviewResult.PASS,
                    InterviewResult.FAIL,
                    InterviewResult.FOR_CLIENT_INTERVIEW,
                    InterviewResult.ON_HOLD
            );
            case CLIENT -> List.of(
                    InterviewResult.PASS,
                    InterviewResult.FAIL,
                    InterviewResult.ON_HOLD
            );
        };
    }

    public void validate(InterviewStage stage, InterviewResult result) {
        if (result == null) {
            throw new BusinessRuleViolationException("Interview result is required.");
        }
        if (!allowedResults(stage).contains(result)) {
            throw new BusinessRuleViolationException(
                    result.name() + " is not a valid result for a " + stage.name() + " interview."
            );
        }
    }
}
