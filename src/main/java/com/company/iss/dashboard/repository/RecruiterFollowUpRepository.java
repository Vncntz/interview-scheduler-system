package com.company.iss.dashboard.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.evaluation.entity.InterviewResult;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecruiterFollowUpRepository extends Repository<Applicant, Long> {

    @Query("""
            select
                a.id as applicantId,
                a.branch.id as branchId,
                trim(concat(
                    concat(a.firstName, ' '),
                    concat(
                        case
                            when a.middleName is null or trim(a.middleName) = '' then ''
                            else concat(a.middleName, ' ')
                        end,
                        a.lastName
                    )
                )) as applicantName,
                p.title as positionTitle,
                c.companyName as clientName,
                a.status as applicantStatus,
                latestBooking.interviewStage as mostRecentBookingStage,
                latestBooking.status as mostRecentBookingStatus,
                case
                    when a.status in :progressionStatuses then progression.evaluationDate
                    else latestBooking.updatedAt
                end as waitingSince
            from Applicant a
            left join a.positionOpening p
            left join p.client c
            left join InterviewEvaluation progression
                on progression.applicant = a
                and ((a.status = :finalStatus and progression.result = :finalResult)
                    or (a.status = :clientStatus and progression.result = :clientResult))
                and not exists (
                    select newerProgression.id
                    from InterviewEvaluation newerProgression
                    where newerProgression.applicant = a
                      and newerProgression.result = progression.result
                      and (newerProgression.evaluationDate > progression.evaluationDate
                          or (newerProgression.evaluationDate = progression.evaluationDate
                              and newerProgression.id > progression.id))
                )
            left join Booking latestBooking
                on latestBooking.applicant = a
                and not exists (
                    select newerBooking.id
                    from Booking newerBooking
                    where newerBooking.applicant = a
                      and (newerBooking.bookedDateTime > latestBooking.bookedDateTime
                          or (newerBooking.bookedDateTime = latestBooking.bookedDateTime
                              and newerBooking.id > latestBooking.id))
                )
            where a.active = true
              and a.branch.id = :branchId
              and not exists (
                  select activeBooking.id
                  from Booking activeBooking
                  where activeBooking.applicant = a
                    and activeBooking.status in :activeStatuses
              )
              and (
                  (a.status in :progressionStatuses and progression.id is not null)
                  or (a.status = :scheduledStatus
                      and latestBooking.status in :replacementStatuses
                      and latestBooking.interviewStage in :followUpStages)
              )
            order by
                case
                    when a.status in :progressionStatuses then progression.evaluationDate
                    else latestBooking.updatedAt
                end,
                a.id
            """)
    List<FollowUpApplicantProjection> findFollowUps(
            @Param("branchId") Long branchId,
            @Param("finalStatus") ApplicantStatus finalStatus,
            @Param("clientStatus") ApplicantStatus clientStatus,
            @Param("scheduledStatus") ApplicantStatus scheduledStatus,
            @Param("progressionStatuses") List<ApplicantStatus> progressionStatuses,
            @Param("finalResult") InterviewResult finalResult,
            @Param("clientResult") InterviewResult clientResult,
            @Param("replacementStatuses") List<BookingStatus> replacementStatuses,
            @Param("followUpStages") List<InterviewStage> followUpStages,
            @Param("activeStatuses") List<BookingStatus> activeStatuses
    );
}
