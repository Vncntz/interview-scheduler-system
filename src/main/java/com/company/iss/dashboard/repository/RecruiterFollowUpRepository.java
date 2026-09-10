package com.company.iss.dashboard.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingLifecycleAction;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RecruiterFollowUpRepository extends Repository<Applicant, Long> {

    @Query("""
            select
                a.id as applicantId,
                a.branch.id as branchId,
                trim(concat(concat(a.firstName, ' '), concat(
                    case when a.middleName is null or trim(a.middleName) = '' then ''
                         else concat(a.middleName, ' ') end,
                    a.lastName))) as applicantName,
                p.title as positionTitle,
                c.companyName as clientName,
                a.status as applicantStatus,
                latestBooking.interviewStage as mostRecentBookingStage,
                latestBooking.status as mostRecentBookingStatus,
                case when a.status = :requiredStatus then progression.evaluationDate
                     else lifecycle.occurredAt end as waitingSince,
                case when a.status = :requiredStatus then progressionLifecycle.appointmentDate
                     else lifecycle.appointmentDate end as relatedAppointmentDate,
                case when a.status = :requiredStatus then progressionLifecycle.startTime
                     else lifecycle.startTime end as relatedAppointmentStartTime
            from Applicant a
            left join a.positionOpening p
            left join p.client c
            left join InterviewEvaluation progression
                on progression.applicant = a
                and progression.result = :requiredResult
                and not exists (
                    select newerProgression.id from InterviewEvaluation newerProgression
                    where newerProgression.applicant = a
                      and newerProgression.result = progression.result
                      and (newerProgression.evaluationDate > progression.evaluationDate
                          or (newerProgression.evaluationDate = progression.evaluationDate
                              and newerProgression.id > progression.id)))
            left join Booking latestBooking
                on latestBooking.applicant = a
                and not exists (
                    select newerBooking.id from Booking newerBooking
                    where newerBooking.applicant = a
                      and (newerBooking.bookedDateTime > latestBooking.bookedDateTime
                          or (newerBooking.bookedDateTime = latestBooking.bookedDateTime
                              and newerBooking.id > latestBooking.id)))
            left join BookingLifecycleHistory lifecycle
                on lifecycle.booking = latestBooking
                and ((latestBooking.status = :cancelledStatus and lifecycle.action = :cancelledAction)
                    or (latestBooking.status = :noShowStatus and lifecycle.action = :noShowAction))
            left join progression.booking progressionBooking
            left join BookingLifecycleHistory progressionLifecycle
                on progressionLifecycle.booking = progressionBooking
                and progressionLifecycle.action = :attendedAction
            where a.active = true
              and a.branch.id = :branchId
              and not exists (
                  select activeBooking.id from Booking activeBooking
                  where activeBooking.applicant = a and activeBooking.status in :activeStatuses)
              and ((a.status = :requiredStatus and progression.id is not null)
                  or (a.status = :scheduledStatus
                      and latestBooking.status in :replacementStatuses
                      and latestBooking.interviewStage = :requiredStage))
              and (
                  :deadlineFilter = 'ALL'
                  or (:deadlineFilter = 'TIMING_UNAVAILABLE'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) is null)
                  or (:deadlineFilter = 'OVERDUE'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) <= :overdueCutoff)
                  or (:deadlineFilter = 'DUE_SOON'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) > :overdueCutoff
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) <= :dueSoonCutoff)
                  or (:deadlineFilter = 'ON_TRACK'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) > :dueSoonCutoff)
              )
            order by
                case
                    when (case when a.status = :requiredStatus then progression.evaluationDate
                               else lifecycle.occurredAt end) <= :overdueCutoff then 0
                    when (case when a.status = :requiredStatus then progression.evaluationDate
                               else lifecycle.occurredAt end) <= :dueSoonCutoff then 1
                    when (case when a.status = :requiredStatus then progression.evaluationDate
                               else lifecycle.occurredAt end) is not null then 2
                    else 3
                end,
                case when a.status = :requiredStatus then progression.evaluationDate
                     else lifecycle.occurredAt end,
                a.id
            """)
    List<FollowUpApplicantProjection> findFollowUpsByStage(
            @Param("branchId") Long branchId,
            @Param("requiredStage") InterviewStage requiredStage,
            @Param("requiredStatus") ApplicantStatus requiredStatus,
            @Param("scheduledStatus") ApplicantStatus scheduledStatus,
            @Param("requiredResult") InterviewResult requiredResult,
            @Param("replacementStatuses") List<BookingStatus> replacementStatuses,
            @Param("activeStatuses") List<BookingStatus> activeStatuses,
            @Param("cancelledStatus") BookingStatus cancelledStatus,
            @Param("noShowStatus") BookingStatus noShowStatus,
            @Param("cancelledAction") BookingLifecycleAction cancelledAction,
            @Param("noShowAction") BookingLifecycleAction noShowAction,
            @Param("attendedAction") BookingLifecycleAction attendedAction,
            @Param("deadlineFilter") String deadlineFilter,
            @Param("overdueCutoff") LocalDateTime overdueCutoff,
            @Param("dueSoonCutoff") LocalDateTime dueSoonCutoff,
            Pageable pageable
    );

    @Query("""
            select count(a.id)
            from Applicant a
            left join InterviewEvaluation progression
                on progression.applicant = a
                and progression.result = :requiredResult
                and not exists (
                    select newerProgression.id from InterviewEvaluation newerProgression
                    where newerProgression.applicant = a
                      and newerProgression.result = progression.result
                      and (newerProgression.evaluationDate > progression.evaluationDate
                          or (newerProgression.evaluationDate = progression.evaluationDate
                              and newerProgression.id > progression.id)))
            left join Booking latestBooking
                on latestBooking.applicant = a
                and not exists (
                    select newerBooking.id from Booking newerBooking
                    where newerBooking.applicant = a
                      and (newerBooking.bookedDateTime > latestBooking.bookedDateTime
                          or (newerBooking.bookedDateTime = latestBooking.bookedDateTime
                              and newerBooking.id > latestBooking.id)))
            left join BookingLifecycleHistory lifecycle
                on lifecycle.booking = latestBooking
                and ((latestBooking.status = :cancelledStatus and lifecycle.action = :cancelledAction)
                    or (latestBooking.status = :noShowStatus and lifecycle.action = :noShowAction))
            where a.active = true
              and a.branch.id = :branchId
              and not exists (
                  select activeBooking.id from Booking activeBooking
                  where activeBooking.applicant = a and activeBooking.status in :activeStatuses)
              and ((a.status = :requiredStatus and progression.id is not null)
                  or (a.status = :scheduledStatus
                      and latestBooking.status in :replacementStatuses
                      and latestBooking.interviewStage = :requiredStage))
              and (
                  :deadlineFilter = 'ALL'
                  or (:deadlineFilter = 'TIMING_UNAVAILABLE'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) is null)
                  or (:deadlineFilter = 'OVERDUE'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) <= :overdueCutoff)
                  or (:deadlineFilter = 'DUE_SOON'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) > :overdueCutoff
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) <= :dueSoonCutoff)
                  or (:deadlineFilter = 'ON_TRACK'
                      and (case when a.status = :requiredStatus then progression.evaluationDate
                                else lifecycle.occurredAt end) > :dueSoonCutoff)
              )
            """)
    long countFollowUpsByStage(
            @Param("branchId") Long branchId,
            @Param("requiredStage") InterviewStage requiredStage,
            @Param("requiredStatus") ApplicantStatus requiredStatus,
            @Param("scheduledStatus") ApplicantStatus scheduledStatus,
            @Param("requiredResult") InterviewResult requiredResult,
            @Param("replacementStatuses") List<BookingStatus> replacementStatuses,
            @Param("activeStatuses") List<BookingStatus> activeStatuses,
            @Param("cancelledStatus") BookingStatus cancelledStatus,
            @Param("noShowStatus") BookingStatus noShowStatus,
            @Param("cancelledAction") BookingLifecycleAction cancelledAction,
            @Param("noShowAction") BookingLifecycleAction noShowAction,
            @Param("deadlineFilter") String deadlineFilter,
            @Param("overdueCutoff") LocalDateTime overdueCutoff,
            @Param("dueSoonCutoff") LocalDateTime dueSoonCutoff
    );

    @Query("""
            select
                count(a.id) as total,
                coalesce(sum(case when (case when a.status = :requiredStatus
                    then progression.evaluationDate else lifecycle.occurredAt end) > :dueSoonCutoff
                    then 1 else 0 end), 0) as onTrack,
                coalesce(sum(case when (case when a.status = :requiredStatus
                    then progression.evaluationDate else lifecycle.occurredAt end) > :overdueCutoff
                    and (case when a.status = :requiredStatus
                    then progression.evaluationDate else lifecycle.occurredAt end) <= :dueSoonCutoff
                    then 1 else 0 end), 0) as dueSoon,
                coalesce(sum(case when (case when a.status = :requiredStatus
                    then progression.evaluationDate else lifecycle.occurredAt end) <= :overdueCutoff
                    then 1 else 0 end), 0) as overdue,
                coalesce(sum(case when (case when a.status = :requiredStatus
                    then progression.evaluationDate else lifecycle.occurredAt end) is null
                    then 1 else 0 end), 0) as timingUnavailable
            from Applicant a
            left join InterviewEvaluation progression
                on progression.applicant = a
                and progression.result = :requiredResult
                and not exists (
                    select newerProgression.id from InterviewEvaluation newerProgression
                    where newerProgression.applicant = a
                      and newerProgression.result = progression.result
                      and (newerProgression.evaluationDate > progression.evaluationDate
                          or (newerProgression.evaluationDate = progression.evaluationDate
                              and newerProgression.id > progression.id)))
            left join Booking latestBooking
                on latestBooking.applicant = a
                and not exists (
                    select newerBooking.id from Booking newerBooking
                    where newerBooking.applicant = a
                      and (newerBooking.bookedDateTime > latestBooking.bookedDateTime
                          or (newerBooking.bookedDateTime = latestBooking.bookedDateTime
                              and newerBooking.id > latestBooking.id)))
            left join BookingLifecycleHistory lifecycle
                on lifecycle.booking = latestBooking
                and ((latestBooking.status = :cancelledStatus and lifecycle.action = :cancelledAction)
                    or (latestBooking.status = :noShowStatus and lifecycle.action = :noShowAction))
            where a.active = true
              and a.branch.id = :branchId
              and not exists (
                  select activeBooking.id from Booking activeBooking
                  where activeBooking.applicant = a and activeBooking.status in :activeStatuses)
              and ((a.status = :requiredStatus and progression.id is not null)
                  or (a.status = :scheduledStatus
                      and latestBooking.status in :replacementStatuses
                      and latestBooking.interviewStage = :requiredStage))
            """)
    FollowUpWorkloadProjection summarizeFollowUpsByStage(
            @Param("branchId") Long branchId,
            @Param("requiredStage") InterviewStage requiredStage,
            @Param("requiredStatus") ApplicantStatus requiredStatus,
            @Param("scheduledStatus") ApplicantStatus scheduledStatus,
            @Param("requiredResult") InterviewResult requiredResult,
            @Param("replacementStatuses") List<BookingStatus> replacementStatuses,
            @Param("activeStatuses") List<BookingStatus> activeStatuses,
            @Param("cancelledStatus") BookingStatus cancelledStatus,
            @Param("noShowStatus") BookingStatus noShowStatus,
            @Param("cancelledAction") BookingLifecycleAction cancelledAction,
            @Param("noShowAction") BookingLifecycleAction noShowAction,
            @Param("overdueCutoff") LocalDateTime overdueCutoff,
            @Param("dueSoonCutoff") LocalDateTime dueSoonCutoff
    );
}
