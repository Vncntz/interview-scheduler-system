package com.company.iss.hiring.repository;

import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.hiring.entity.HiringDecision;
import com.company.iss.hiring.entity.HiringDecisionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HiringDecisionRepository extends JpaRepository<HiringDecision, Long> {

    boolean existsByApplicantId(Long applicantId);

    @EntityGraph(attributePaths = {"applicant", "evaluation", "evaluation.booking", "position", "offeredBy", "resolvedBy"})
    Optional<HiringDecision> findByApplicantId(Long applicantId);

    @Query("""
            select count(e) > 0 from InterviewEvaluation e
            join e.applicant a
            join e.booking b
            join a.positionOpening p
            where a.id = :applicantId
              and e.result = com.company.iss.evaluation.entity.InterviewResult.PASS
              and b.status = com.company.iss.booking.entity.BookingStatus.PASSED
              and b.applicant = a
              and a.active = true
              and a.status = com.company.iss.applicant.entity.ApplicantStatus.PASSED
              and p.active = true
              and p.status = com.company.iss.position.entity.PositionStatus.OPEN
              and p.hiredCount < p.requiredHeadcount
              and not exists (select d.id from HiringDecision d where d.applicant = a or d.evaluation = e)
            """)
    boolean existsEligibleEvaluationByApplicantId(@Param("applicantId") Long applicantId);

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
    @Query("""
            select d from HiringDecision d
            join d.applicant a
            join a.branch branch
            join d.position p
            left join p.client client
            where (:branchId is null or branch.id = :branchId)
              and d.status = com.company.iss.hiring.entity.HiringDecisionStatus.OFFERED
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0
                   or (:statusKeywordMatches = true))
              and (:deadlineFilter = 'ALL'
                   or (:deadlineFilter = 'OVERDUE'
                       and d.responseDueAt is not null and d.responseDueAt <= :now)
                   or (:deadlineFilter = 'DUE_SOON'
                       and d.responseDueAt > :now and d.responseDueAt <= :dueSoonCutoff)
                   or (:deadlineFilter = 'ON_TRACK'
                       and d.responseDueAt > :dueSoonCutoff)
                   or (:deadlineFilter = 'NO_DEADLINE'
                       and d.responseDueAt is null))
            """)
    List<HiringDecision> findOutstandingDecisionPage(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("statusKeywordMatches") boolean statusKeywordMatches,
            @Param("deadlineFilter") String deadlineFilter,
            @Param("now") java.time.LocalDateTime now,
            @Param("dueSoonCutoff") java.time.LocalDateTime dueSoonCutoff,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    @Query("""
            select d from HiringDecision d
            join d.applicant a
            join a.branch branch
            join d.position p
            left join p.client client
            where (:branchId is null or branch.id = :branchId)
              and d.status = com.company.iss.hiring.entity.HiringDecisionStatus.OFFERED
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0
                   or (:statusKeywordMatches = true))
              and (:deadlineFilter = 'ALL'
                   or (:deadlineFilter = 'OVERDUE'
                       and d.responseDueAt is not null and d.responseDueAt <= :now)
                   or (:deadlineFilter = 'DUE_SOON'
                       and d.responseDueAt > :now and d.responseDueAt <= :dueSoonCutoff)
                   or (:deadlineFilter = 'ON_TRACK'
                       and d.responseDueAt > :dueSoonCutoff)
                   or (:deadlineFilter = 'NO_DEADLINE'
                       and d.responseDueAt is null))
            order by
              case
                when d.responseDueAt is not null and d.responseDueAt <= :now then 0
                when d.responseDueAt > :now and d.responseDueAt <= :dueSoonCutoff then 1
                when d.responseDueAt > :dueSoonCutoff then 2
                else 3
              end,
              d.responseDueAt asc,
              d.id asc
            """)
    List<HiringDecision> findOutstandingDecisionPageByDeadlinePriority(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("statusKeywordMatches") boolean statusKeywordMatches,
            @Param("deadlineFilter") String deadlineFilter,
            @Param("now") java.time.LocalDateTime now,
            @Param("dueSoonCutoff") java.time.LocalDateTime dueSoonCutoff,
            Pageable pageable
    );

    @Query("""
            select count(d) from HiringDecision d
            join d.applicant a
            join a.branch branch
            join d.position p
            left join p.client client
            where (:branchId is null or branch.id = :branchId)
              and d.status = com.company.iss.hiring.entity.HiringDecisionStatus.OFFERED
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0
                   or (:statusKeywordMatches = true))
              and (:deadlineFilter = 'ALL'
                   or (:deadlineFilter = 'OVERDUE'
                       and d.responseDueAt is not null and d.responseDueAt <= :now)
                   or (:deadlineFilter = 'DUE_SOON'
                       and d.responseDueAt > :now and d.responseDueAt <= :dueSoonCutoff)
                   or (:deadlineFilter = 'ON_TRACK'
                       and d.responseDueAt > :dueSoonCutoff)
                   or (:deadlineFilter = 'NO_DEADLINE'
                       and d.responseDueAt is null))
            """)
    long countOutstandingDecisions(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("statusKeywordMatches") boolean statusKeywordMatches,
            @Param("deadlineFilter") String deadlineFilter,
            @Param("now") java.time.LocalDateTime now,
            @Param("dueSoonCutoff") java.time.LocalDateTime dueSoonCutoff
    );

    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "evaluation", "position", "position.client", "offeredBy", "resolvedBy"
    })
    @Query("""
            select d from HiringDecision d
            join d.applicant a
            join a.branch branch
            join d.position p
            left join p.client client
            where (:branchId is null or branch.id = :branchId)
              and d.status in :statuses
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0
                   or (:statusKeywordMatches = true and d.status in :matchingStatuses))
            """)
    List<HiringDecision> findDecisionPage(
            @Param("branchId") Long branchId,
            @Param("statuses") List<HiringDecisionStatus> statuses,
            @Param("keyword") String keyword,
            @Param("statusKeywordMatches") boolean statusKeywordMatches,
            @Param("matchingStatuses") List<HiringDecisionStatus> matchingStatuses,
            Pageable pageable
    );

    @Query("""
            select count(d) from HiringDecision d
            join d.applicant a
            join a.branch branch
            join d.position p
            left join p.client client
            where (:branchId is null or branch.id = :branchId)
              and d.status in :statuses
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0
                   or (:statusKeywordMatches = true and d.status in :matchingStatuses))
            """)
    long countDecisionPage(
            @Param("branchId") Long branchId,
            @Param("statuses") List<HiringDecisionStatus> statuses,
            @Param("keyword") String keyword,
            @Param("statusKeywordMatches") boolean statusKeywordMatches,
            @Param("matchingStatuses") List<HiringDecisionStatus> matchingStatuses
    );

    @Query("""
            select e from InterviewEvaluation e
            join fetch e.applicant a
            join fetch a.branch branch
            join fetch a.positionOpening p
            left join fetch p.client client
            join fetch e.booking b
            where e.result = com.company.iss.evaluation.entity.InterviewResult.PASS
              and b.status = com.company.iss.booking.entity.BookingStatus.PASSED
              and b.applicant = a
              and a.active = true
              and a.status = com.company.iss.applicant.entity.ApplicantStatus.PASSED
              and p.active = true
              and p.status = com.company.iss.position.entity.PositionStatus.OPEN
              and p.hiredCount < p.requiredHeadcount
              and (:branchId is null or branch.id = :branchId)
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0)
              and not exists (select d.id from HiringDecision d where d.applicant = a or d.evaluation = e)
              and not exists (
                  select newer.id from InterviewEvaluation newer
                  join newer.booking newerBooking
                  where newer.applicant = a
                    and newer.result = com.company.iss.evaluation.entity.InterviewResult.PASS
                    and newerBooking.status = com.company.iss.booking.entity.BookingStatus.PASSED
                    and newerBooking.applicant = a
                    and (newer.evaluationDate > e.evaluationDate
                         or (newer.evaluationDate = e.evaluationDate and newer.id > e.id))
              )
            """)
    List<InterviewEvaluation> findEligibleEvaluationPage(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select count(e) from InterviewEvaluation e
            join e.applicant a
            join a.branch branch
            join a.positionOpening p
            left join p.client client
            join e.booking b
            where e.result = com.company.iss.evaluation.entity.InterviewResult.PASS
              and b.status = com.company.iss.booking.entity.BookingStatus.PASSED
              and b.applicant = a
              and a.active = true
              and a.status = com.company.iss.applicant.entity.ApplicantStatus.PASSED
              and p.active = true
              and p.status = com.company.iss.position.entity.PositionStatus.OPEN
              and p.hiredCount < p.requiredHeadcount
              and (:branchId is null or branch.id = :branchId)
              and (:keyword is null
                   or locate(:keyword, lower(a.firstName)) > 0
                   or locate(:keyword, lower(coalesce(a.middleName, ''))) > 0
                   or locate(:keyword, lower(a.lastName)) > 0
                   or locate(:keyword, lower(concat(a.firstName, concat(' ', a.lastName)))) > 0
                   or locate(:keyword, lower(concat(a.firstName,
                       concat(case when a.middleName is null or trim(a.middleName) = ''
                                   then ' ' else concat(' ', concat(trim(a.middleName), ' ')) end,
                              a.lastName)))) > 0
                   or locate(:keyword, lower(branch.branchName)) > 0
                   or locate(:keyword, lower(p.title)) > 0
                   or locate(:keyword, lower(coalesce(client.companyName, ''))) > 0)
              and not exists (select d.id from HiringDecision d where d.applicant = a or d.evaluation = e)
              and not exists (
                  select newer.id from InterviewEvaluation newer
                  join newer.booking newerBooking
                  where newer.applicant = a
                    and newer.result = com.company.iss.evaluation.entity.InterviewResult.PASS
                    and newerBooking.status = com.company.iss.booking.entity.BookingStatus.PASSED
                    and newerBooking.applicant = a
                    and (newer.evaluationDate > e.evaluationDate
                         or (newer.evaluationDate = e.evaluationDate and newer.id > e.id))
              )
            """)
    long countEligibleEvaluationPage(@Param("branchId") Long branchId, @Param("keyword") String keyword);
}
