package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByApplicant(Applicant applicant);

    Optional<Booking> findFirstByApplicantOrderByBookedDateTimeDescIdDesc(Applicant applicant);

    @EntityGraph(attributePaths = {"schedule", "schedule.recruiter", "schedule.branch", "recruiter"})
    List<Booking> findByApplicantIdOrderByBookedDateTimeAscIdAsc(Long applicantId);

    List<Booking> findBySchedule(Schedule schedule);

    List<Booking> findByStatus(BookingStatus status);

    Optional<Booking> findFirstByApplicantAndStatusIn(Applicant applicant, List<BookingStatus> statuses);

    boolean existsByApplicantIdAndStatusIn(Long applicantId, List<BookingStatus> statuses);

    boolean existsByScheduleId(Long scheduleId);

    Long countByStatus(BookingStatus status);

    Optional<Booking> findByApplicantAndSchedule(Applicant applicant, Schedule schedule);

    @EntityGraph(attributePaths = {
            "applicant",
            "applicant.branch",
            "applicant.positionOpening",
            "applicant.positionOpening.client",
            "schedule",
            "schedule.branch",
            "schedule.recruiter",
            "recruiter"
    })
    @Query("""
            select b from Booking b
            where (:branchId is null or b.applicant.branch.id = :branchId)
              and (:keyword is null
                   or lower(b.bookingReference) like concat('%', :keyword, '%')
                   or lower(b.applicant.firstName) like concat('%', :keyword, '%')
                   or lower(b.applicant.lastName) like concat('%', :keyword, '%')
                   or lower(concat(
                       concat(b.applicant.firstName, ' '),
                       concat(
                           case
                               when b.applicant.middleName is null or trim(b.applicant.middleName) = '' then ''
                               else concat(b.applicant.middleName, ' ')
                           end,
                           b.applicant.lastName
                       )
                   )) like concat('%', :keyword, '%'))
              and (:status is null or b.status = :status)
              and (:scheduleDate is null or b.schedule.scheduleDate = :scheduleDate)
            order by b.schedule.scheduleDate desc, b.schedule.startTime desc, b.id desc
            """)
    List<Booking> findGridPage(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("status") BookingStatus status,
            @Param("scheduleDate") LocalDate scheduleDate,
            Pageable pageable
    );

    @Query("""
            select count(b) from Booking b
            where (:branchId is null or b.applicant.branch.id = :branchId)
              and (:keyword is null
                   or lower(b.bookingReference) like concat('%', :keyword, '%')
                   or lower(b.applicant.firstName) like concat('%', :keyword, '%')
                   or lower(b.applicant.lastName) like concat('%', :keyword, '%')
                   or lower(concat(
                       concat(b.applicant.firstName, ' '),
                       concat(
                           case
                               when b.applicant.middleName is null or trim(b.applicant.middleName) = '' then ''
                               else concat(b.applicant.middleName, ' ')
                           end,
                           b.applicant.lastName
                       )
                   )) like concat('%', :keyword, '%'))
              and (:status is null or b.status = :status)
              and (:scheduleDate is null or b.schedule.scheduleDate = :scheduleDate)
            """)
    long countGrid(
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            @Param("status") BookingStatus status,
            @Param("scheduleDate") LocalDate scheduleDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "applicant", "applicant.branch", "applicant.positionOpening", "applicant.positionOpening.client",
            "schedule", "schedule.branch", "schedule.recruiter", "recruiter"
    })
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select b.id
            from Booking b
            where b.status in :statuses
              and b.applicant.active = true
              and b.applicant.status = com.company.iss.applicant.entity.ApplicantStatus.SCHEDULED
              and b.applicant.email is not null
              and trim(b.applicant.email) <> ''
              and b.schedule.active = true
              and b.schedule.status <> com.company.iss.schedule.entity.ScheduleStatus.CANCELLED
              and (b.schedule.scheduleDate > :lowerDate
                   or (b.schedule.scheduleDate = :lowerDate and b.schedule.startTime > :lowerTime))
              and (b.schedule.scheduleDate < :upperDate
                   or (b.schedule.scheduleDate = :upperDate and b.schedule.startTime <= :upperTime))
              and not exists (
                  select d.id from InterviewReminderDelivery d
                  where d.booking = b
                    and d.reminderGeneration = b.reminderGeneration
                    and d.reminderType = :reminderType
              )
            order by b.schedule.scheduleDate, b.schedule.startTime, b.id
            """)
    List<Long> findReminderCandidateIds(
            @Param("statuses") List<BookingStatus> statuses,
            @Param("reminderType") com.company.iss.notification.entity.InterviewReminderType reminderType,
            @Param("lowerDate") LocalDate lowerDate,
            @Param("lowerTime") LocalTime lowerTime,
            @Param("upperDate") LocalDate upperDate,
            @Param("upperTime") LocalTime upperTime,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    List<Booking> findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
            Long recruiterId, LocalDate scheduleDate, List<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.recruiter.id = :recruiterId
              and b.status in :statuses
              and (b.schedule.scheduleDate > :today
                   or (b.schedule.scheduleDate = :today and b.schedule.startTime > :now))
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findUpcomingAssigned(
            @Param("recruiterId") Long recruiterId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            @Param("statuses") List<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    List<Booking> findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
            Long branchId, BookingStatus status
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.branch.id = :branchId and b.status = :status
              and (b.schedule.scheduleDate < :today
                   or (b.schedule.scheduleDate = :today and b.schedule.endTime <= :now))
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findDueByBranchAndStatus(
            @Param("branchId") Long branchId,
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.branch.id = :branchId and b.status = :status
              and (b.schedule.scheduleDate < :today
                   or (b.schedule.scheduleDate = :today and b.schedule.endTime <= :now))
              and not exists (select e.id from InterviewEvaluation e where e.booking = b)
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findOverdueUnevaluatedByBranch(
            @Param("branchId") Long branchId,
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @EntityGraph(attributePaths = {
            "applicant",
            "applicant.positionOpening",
            "applicant.positionOpening.client",
            "schedule",
            "schedule.recruiter"
    })
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
                select b
                from Booking b
                where b.status in :statuses
                  and b.schedule.active = true
                  and b.schedule.status <> :cancelledScheduleStatus
                  and (
                      b.schedule.scheduleDate > :today
                      or (
                          b.schedule.scheduleDate = :today
                          and b.schedule.endTime >= :currentTime
                      )
                  )
                order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findUpcoming(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("cancelledScheduleStatus") ScheduleStatus cancelledScheduleStatus,
            Pageable pageable
    );
}
