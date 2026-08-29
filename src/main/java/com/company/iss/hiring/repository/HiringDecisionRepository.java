package com.company.iss.hiring.repository;

import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HiringDecisionRepository extends JpaRepository<HiringDecision, Long> {

    boolean existsByApplicantId(Long applicantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"applicant", "applicant.branch", "evaluation", "position", "position.client"})
    @Query("select d from HiringDecision d where d.applicant.id = :applicantId")
    Optional<HiringDecision> findByApplicantIdForUpdate(@Param("applicantId") Long applicantId);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    @Query("select d from HiringDecision d where d.id = :id")
    Optional<HiringDecision> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    List<HiringDecision> findByStatusOrderByOfferedAtDesc(HiringDecisionStatus status);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    List<HiringDecision> findByStatusAndApplicantBranchIdOrderByOfferedAtDesc(
            HiringDecisionStatus status,
            Long branchId
    );

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    List<HiringDecision> findByStatusInOrderByResolvedAtDesc(List<HiringDecisionStatus> statuses);

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    List<HiringDecision> findByStatusInAndApplicantBranchIdOrderByResolvedAtDesc(
            List<HiringDecisionStatus> statuses,
            Long branchId
    );

    @Query("""
            select e from InterviewEvaluation e
            join fetch e.applicant a
            join fetch a.branch
            join fetch a.positionOpening p
            left join fetch p.client
            join fetch e.booking b
            where e.result = com.company.iss.evaluation.entity.InterviewResult.PASS
              and b.status = com.company.iss.booking.entity.BookingStatus.PASSED
              and b.applicant = a
              and a.active = true
              and a.status = com.company.iss.applicant.entity.ApplicantStatus.PASSED
              and p.active = true
              and p.status = com.company.iss.position.entity.PositionStatus.OPEN
              and p.hiredCount < p.requiredHeadcount
              and not exists (select d.id from HiringDecision d where d.applicant = a or d.evaluation = e)
            order by e.evaluationDate desc
            """)
    List<InterviewEvaluation> findEligibleEvaluations();

    @Query("""
            select e from InterviewEvaluation e
            join fetch e.applicant a
            join fetch a.branch branch
            join fetch a.positionOpening p
            left join fetch p.client
            join fetch e.booking b
            where branch.id = :branchId
              and e.result = com.company.iss.evaluation.entity.InterviewResult.PASS
              and b.status = com.company.iss.booking.entity.BookingStatus.PASSED
              and b.applicant = a
              and a.active = true
              and a.status = com.company.iss.applicant.entity.ApplicantStatus.PASSED
              and p.active = true
              and p.status = com.company.iss.position.entity.PositionStatus.OPEN
              and p.hiredCount < p.requiredHeadcount
              and not exists (select d.id from HiringDecision d where d.applicant = a or d.evaluation = e)
            order by e.evaluationDate desc
            """)
    List<InterviewEvaluation> findEligibleEvaluationsByBranchId(@Param("branchId") Long branchId);
}
