package com.company.iss.evaluation.repository;

import com.company.iss.evaluation.entity.InterviewEvaluation;

public interface InterviewEvaluationAppender {

    InterviewEvaluation append(InterviewEvaluation evaluation);
}
