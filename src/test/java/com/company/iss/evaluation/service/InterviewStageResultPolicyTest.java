package com.company.iss.evaluation.service;

import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewStageResultPolicyTest {

    private final InterviewStageResultPolicy policy = new InterviewStageResultPolicy();

    @Test
    void initialAllowsEveryCurrentEvaluationResult() {
        assertEquals(List.of(InterviewResult.values()), policy.allowedResults(InterviewStage.INITIAL));
    }

    @Test
    void finalAllowsProgressionToClientButNotAnotherFinalInterview() {
        assertEquals(
                List.of(
                        InterviewResult.PASS,
                        InterviewResult.FAIL,
                        InterviewResult.FOR_CLIENT_INTERVIEW,
                        InterviewResult.ON_HOLD
                ),
                policy.allowedResults(InterviewStage.FINAL)
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validate(InterviewStage.FINAL, InterviewResult.FOR_FINAL_INTERVIEW)
        );
    }

    @Test
    void clientAllowsOnlyTerminalOrHoldResults() {
        assertEquals(
                List.of(InterviewResult.PASS, InterviewResult.FAIL, InterviewResult.ON_HOLD),
                policy.allowedResults(InterviewStage.CLIENT)
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validate(InterviewStage.CLIENT, InterviewResult.FOR_FINAL_INTERVIEW)
        );
        assertThrows(
                BusinessRuleViolationException.class,
                () -> policy.validate(InterviewStage.CLIENT, InterviewResult.FOR_CLIENT_INTERVIEW)
        );
    }
}
