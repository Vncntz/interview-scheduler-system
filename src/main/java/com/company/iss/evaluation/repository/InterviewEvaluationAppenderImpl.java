package com.company.iss.evaluation.repository;

import com.company.iss.evaluation.entity.InterviewEvaluation;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class InterviewEvaluationAppenderImpl implements InterviewEvaluationAppender {

    private final EntityManager entityManager;

    public InterviewEvaluationAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public InterviewEvaluation append(InterviewEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation is required");
        if (evaluation.getId() != null) {
            throw new IllegalArgumentException("Persisted interview evaluation cannot be appended again.");
        }
        entityManager.persist(evaluation);
        return evaluation;
    }
}
