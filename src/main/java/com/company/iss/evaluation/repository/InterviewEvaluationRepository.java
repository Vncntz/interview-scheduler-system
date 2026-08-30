package com.company.iss.evaluation.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    boolean existsByBookingId(Long bookingId);

    List<InterviewEvaluation> findByApplicant(Applicant applicant);

    @EntityGraph(attributePaths = {"booking", "booking.schedule", "evaluator"})
    List<InterviewEvaluation> findByApplicantIdOrderByEvaluationDateAscIdAsc(Long applicantId);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "booking", "booking.schedule", "evaluator"})
    List<InterviewEvaluation> findByBookingScheduleBranchIdOrderByEvaluationDateDesc(Long branchId);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "applicant.positionOpening", "applicant.positionOpening.client",
            "booking", "booking.applicant"
    })
    @Query("select e from InterviewEvaluation e where e.id = :id")
    Optional<InterviewEvaluation> findDetailedById(@Param("id") Long id);

}
