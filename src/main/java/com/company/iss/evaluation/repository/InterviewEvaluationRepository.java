package com.company.iss.evaluation.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    boolean existsByBookingId(Long bookingId);

    List<InterviewEvaluation> findByApplicant(Applicant applicant);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "booking", "booking.schedule", "evaluator"})
    List<InterviewEvaluation> findByBookingScheduleBranchIdOrderByEvaluationDateDesc(Long branchId);

}
