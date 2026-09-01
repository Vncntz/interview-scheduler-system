package com.company.iss.evaluation.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "applicant.positionOpening", "applicant.positionOpening.client",
            "booking", "booking.schedule", "evaluator"
    })
    @Query("""
            select e
            from InterviewEvaluation e
            left join e.applicant applicant
            left join applicant.branch branch
            left join applicant.positionOpening position
            left join position.client client
            left join e.evaluator evaluator
            where (:branchId is null or branch.id = :branchId)
              and (
                  :keywordPattern is null
                  or lower(concat(concat(coalesce(applicant.firstName, ''), ' '), coalesce(applicant.lastName, ''))) like :keywordPattern
                  or lower(coalesce(applicant.middleName, '')) like :keywordPattern
                  or lower(coalesce(evaluator.fullName, '')) like :keywordPattern
                  or lower(coalesce(branch.branchName, '')) like :keywordPattern
                  or lower(coalesce(position.title, '')) like :keywordPattern
                  or lower(coalesce(client.companyName, '')) like :keywordPattern
              )
              and (:interviewStage is null or e.booking.interviewStage = :interviewStage)
              and (:result is null or e.result = :result)
              and (:dateFrom is null or e.evaluationDate >= :dateFrom)
              and (:dateTo is null or e.evaluationDate < :dateTo)
            """)
    List<InterviewEvaluation> findGridPage(
            @Param("branchId") Long branchId,
            @Param("keywordPattern") String keywordPattern,
            @Param("interviewStage") InterviewStage interviewStage,
            @Param("result") InterviewResult result,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    @Query("""
            select count(e)
            from InterviewEvaluation e
            left join e.applicant applicant
            left join applicant.branch branch
            left join applicant.positionOpening position
            left join position.client client
            left join e.evaluator evaluator
            where (:branchId is null or branch.id = :branchId)
              and (
                  :keywordPattern is null
                  or lower(concat(concat(coalesce(applicant.firstName, ''), ' '), coalesce(applicant.lastName, ''))) like :keywordPattern
                  or lower(coalesce(applicant.middleName, '')) like :keywordPattern
                  or lower(coalesce(evaluator.fullName, '')) like :keywordPattern
                  or lower(coalesce(branch.branchName, '')) like :keywordPattern
                  or lower(coalesce(position.title, '')) like :keywordPattern
                  or lower(coalesce(client.companyName, '')) like :keywordPattern
              )
              and (:interviewStage is null or e.booking.interviewStage = :interviewStage)
              and (:result is null or e.result = :result)
              and (:dateFrom is null or e.evaluationDate >= :dateFrom)
              and (:dateTo is null or e.evaluationDate < :dateTo)
            """)
    long countGrid(
            @Param("branchId") Long branchId,
            @Param("keywordPattern") String keywordPattern,
            @Param("interviewStage") InterviewStage interviewStage,
            @Param("result") InterviewResult result,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    boolean existsByBookingId(Long bookingId);

    List<InterviewEvaluation> findByApplicant(Applicant applicant);

    @EntityGraph(attributePaths = {"booking", "booking.schedule", "evaluator"})
    List<InterviewEvaluation> findByApplicantIdOrderByEvaluationDateAscIdAsc(Long applicantId);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "applicant.positionOpening", "applicant.positionOpening.client",
            "booking", "booking.applicant"
    })
    @Query("select e from InterviewEvaluation e where e.id = :id")
    Optional<InterviewEvaluation> findDetailedById(@Param("id") Long id);

}
